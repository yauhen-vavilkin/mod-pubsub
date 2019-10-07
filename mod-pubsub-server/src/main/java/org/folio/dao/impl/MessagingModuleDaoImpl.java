package org.folio.dao.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.apache.commons.lang3.StringUtils;
import org.folio.dao.MessagingModuleDao;
import org.folio.dao.ModuleDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.util.DbUtil;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.jaxrs.model.Module;
import org.folio.rest.persist.PostgresClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Implementation for the MessagingModuleDao, works with PostgresClient to access data.
 *
 * @see MessagingModuleDao
 */
@Repository
public class MessagingModuleDaoImpl implements MessagingModuleDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessagingModuleDaoImpl.class);

  private static final String TABLE_NAME = "messaging_module";
  private static final String MODULE_SCHEMA = "pubsub_config";
  private static final String GET_BY_SQL = "SELECT * FROM %s.%s %s";
  private static final String INSERT_BATCH_SQL = "INSERT INTO %s.%s (id, event_type_id, module_id, tenant_id, role, is_applied, subscriber_callback) VALUES ";
  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s.%s WHERE id = ?";
  private static final String DELETE_BY_SQL = "DELETE FROM %s.%s %s";
  private static final String TABLE_COLUMNS_PLACEHOLDER = " (?, ?, ?, ?, ?, ?, ?),";

  @Autowired
  private PostgresClientFactory pgClientFactory;
  @Autowired
  private ModuleDao moduleDao;

  @Override
  public Future<List<MessagingModule>> get(MessagingModuleFilter filter) {
    Future<ResultSet> future = Future.future();
    String preparedQuery = format(GET_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter));
    pgClientFactory.getInstance().select(preparedQuery, future.completer());
    return future.map(this::mapResultSetToMessagingModuleList);
  }

  @Override
  public Future<List<MessagingModule>> save(String moduleName, List<MessagingModule> messagingModules) {
    PostgresClient pgClient = pgClientFactory.getInstance();

    return DbUtil.executeInTransaction(pgClient, connection -> moduleDao.getByName(moduleName, connection)
      .compose(moduleOptional -> moduleOptional
        .map(module -> {
          ModuleRole moduleRole = messagingModules.get(0).getModuleRole();
          String tenantId = messagingModules.get(0).getTenantId();
          return clearPreviousMessagingModulesInfo(module.getId(), moduleRole, tenantId, connection)
            .map(module.getId());
        })
        .orElseGet(() -> moduleDao.save(new Module().withName(moduleName).withId(UUID.randomUUID().toString()), connection)))
      .map(moduleId -> setModuleId(moduleId, messagingModules))
      .compose(messagingModulesList -> saveMessagingModuleList(messagingModulesList, connection)));
  }


  /**
   * Deletes previously created messaging modules with specified moduleId and role by tenant id
   * in specified connection
   *
   * @param moduleId      module id
   * @param role          module role
   * @param tenantId      tenant id
   * @param sqlConnection DB connection
   * @return future with true if succeeded
   */
  private Future<Boolean> clearPreviousMessagingModulesInfo(String moduleId, ModuleRole role,
                                                            String tenantId, AsyncResult<SQLConnection> sqlConnection) {
    MessagingModuleFilter messagingModuleFilter = new MessagingModuleFilter();
    messagingModuleFilter.byModuleRole(role);
    messagingModuleFilter.byTenantId(tenantId);
    return deleteByModuleIdAndFilter(moduleId, messagingModuleFilter, sqlConnection);
  }

  /**
   * Sets moduleId to specified MessagingModule entities
   * @param moduleId module id
   * @param messagingModules MessagingModule entities
   * @return MessagingModule entities list
   */
  private List<MessagingModule> setModuleId(String moduleId, List<MessagingModule> messagingModules) {
    messagingModules.forEach(messagingModule -> messagingModule.setModuleId(moduleId));
    return messagingModules;
  }

  /**
   * Saves list of {@link MessagingModule} to data base with specified moduleName
   * using specified connection.
   *
   * @param messagingModules list of MessagingModule entities
   * @param sqlConnection connection to data base
   * @return future with list of created MessagingModule entities
   */
  private Future<List<MessagingModule>> saveMessagingModuleList(List<MessagingModule> messagingModules,
                                                               AsyncResult<SQLConnection> sqlConnection) {
    Future<UpdateResult> future = Future.future();
    try {
      StringBuilder query = new StringBuilder(format(INSERT_BATCH_SQL, MODULE_SCHEMA, TABLE_NAME));
      JsonArray params = new JsonArray();
      for (MessagingModule messagingModule : messagingModules) {
        query.append(TABLE_COLUMNS_PLACEHOLDER);
        prepareInsertQueryParameters(messagingModule, params);
      }
      String preparedQuery = StringUtils.strip(query.toString(), ",");
      pgClientFactory.getInstance().execute(sqlConnection, preparedQuery, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error saving Messaging Modules", e);
      future.fail(e);
    }
    return future.map(updateResult -> messagingModules);
  }

  private void prepareInsertQueryParameters(MessagingModule messagingModule, JsonArray queryParams) {
    queryParams.add(messagingModule.getId())
      .add(messagingModule.getEventType())
      .add(messagingModule.getModuleId())
      .add(messagingModule.getTenantId())
      .add(messagingModule.getModuleRole().value())
      .add(messagingModule.getApplied());
    String subscriberCallback = messagingModule.getSubscriberCallback();
    queryParams.add(subscriberCallback != null ? subscriberCallback : EMPTY);
  }

  @Override
  public Future<Boolean> delete(String id) {
    Future<UpdateResult> future = Future.future();
    String query = format(DELETE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
    JsonArray params = new JsonArray().add(id);
    pgClientFactory.getInstance().execute(query, params, future.completer());
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<Boolean> deleteByModuleNameAndFilter(String moduleName, MessagingModuleFilter filter) {
    PostgresClient pgClient = pgClientFactory.getInstance();

    return DbUtil.executeInTransaction(pgClient, connection -> moduleDao.getByName(moduleName, connection)
      .compose(moduleOptional -> !moduleOptional.isPresent()
        ? Future.succeededFuture(false)
        : deleteByModuleIdAndFilter(moduleOptional.get().getId(), filter, connection)));
  }

  /**
   *
   * Deletes {@link MessagingModule} by module id and filter
   *
   * @param moduleId module id
   * @param filter messagingModule filter
   * @param sqlConnection DB connection
   * @return future with boolean
   */
  private Future<Boolean> deleteByModuleIdAndFilter(String moduleId, MessagingModuleFilter filter,
                                                    AsyncResult<SQLConnection> sqlConnection) {
    Future<UpdateResult> future = Future.future();
    StringBuilder query = new StringBuilder(format(DELETE_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter)));
    query.append(" AND ")
      .append(" module_id = '").append(moduleId).append("';");
    pgClientFactory.getInstance().execute(sqlConnection, query.toString(), future.completer());
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  private MessagingModule mapRowJsonToMessagingModule(JsonObject rowAsJson) {
    return new MessagingModule()
      .withId(rowAsJson.getString("id"))
      .withEventType(rowAsJson.getString("event_type_id"))
      .withModuleId(rowAsJson.getString("module_id"))
      .withTenantId(rowAsJson.getString("tenant_id"))
      .withModuleRole(ModuleRole.valueOf(rowAsJson.getString("role")))
      .withApplied(rowAsJson.getBoolean("is_applied"))
      .withSubscriberCallback(rowAsJson.getString("subscriber_callback"));
  }

  private List<MessagingModule> mapResultSetToMessagingModuleList(ResultSet resultSet) {
    return resultSet.getRows().stream()
      .map(this::mapRowJsonToMessagingModule)
      .collect(Collectors.toList());
  }

  private String buildWhereClause(MessagingModuleFilter filter) {
    StringBuilder conditionBuilder = new StringBuilder("WHERE TRUE");
    if (filter.getEventType() != null) {
      conditionBuilder.append(" AND event_type_id = '").append(filter.getEventType()).append("'");
    }
    if (filter.getModuleId() != null) {
      conditionBuilder.append(" AND module_id = '").append(filter.getModuleId()).append("'");
    }
    if (filter.getTenantId() != null) {
      conditionBuilder.append(" AND tenant_id = '").append(filter.getTenantId()).append("'");
    }
    if (filter.getModuleRole() != null) {
      conditionBuilder.append(" AND role = '").append(filter.getModuleRole()).append("'");
    }
    if (filter.getApplied() != null) {
      conditionBuilder.append(" AND is_applied = '").append(filter.getApplied()).append("'");
    }
    if (filter.getSubscriberCallback() != null) {
      conditionBuilder.append(" AND subscriber_uscallback = '").append(filter.getSubscriberCallback()).append("'");
    }
    return conditionBuilder.toString();
  }
}
