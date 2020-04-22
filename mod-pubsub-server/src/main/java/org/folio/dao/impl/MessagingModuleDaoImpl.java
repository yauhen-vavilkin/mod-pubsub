package org.folio.dao.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import org.apache.commons.lang3.StringUtils;
import org.folio.dao.MessagingModuleDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.util.DbUtil;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.util.MessagingModuleFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
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
  private static final String INSERT_BATCH_SQL = "INSERT INTO %s.%s (id, event_type_id, module_id, tenant_id, role, activated, subscriber_callback) VALUES ";
  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s.%s WHERE id = ?";
  private static final String DELETE_BY_SQL = "DELETE FROM %s.%s %s";
  private static final String TABLE_COLUMNS_PLACEHOLDER = " (?, ?, ?, ?, ?, ?, ?),";
  private static final String GET_ALL_SQL = "SELECT * FROM %s.%s";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<MessagingModule>> get(MessagingModuleFilter filter) {
    Promise<ResultSet> promise = Promise.promise();
    String preparedQuery = format(GET_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter));
    pgClientFactory.getInstance().select(preparedQuery, promise);
    return promise.future().map(this::mapResultSetToMessagingModuleList);
  }

  @Override
  public Future<List<MessagingModule>> save(List<MessagingModule> messagingModules) {
    PostgresClient pgClient = pgClientFactory.getInstance();
    return DbUtil.executeInTransaction(pgClient, connection ->
      delete(new MessagingModuleFilter()
        .withModuleId(messagingModules.get(0).getModuleId())
        .withModuleRole(messagingModules.get(0).getModuleRole())
        .withTenantId(messagingModules.get(0).getTenantId()), connection)
        .compose(ar -> saveMessagingModuleList(messagingModules, connection)));
  }

  /**
   * Saves list of {@link MessagingModule} to data base with specified moduleName
   * using specified connection.
   *
   * @param messagingModules list of MessagingModule entities
   * @param sqlConnection    connection to data base
   * @return future with list of created MessagingModule entities
   */
  private Future<List<MessagingModule>> saveMessagingModuleList(List<MessagingModule> messagingModules,
                                                                AsyncResult<SQLConnection> sqlConnection) {
    Promise<UpdateResult> promise = Promise.promise();
    try {
      StringBuilder query = new StringBuilder(format(INSERT_BATCH_SQL, MODULE_SCHEMA, TABLE_NAME));
      JsonArray params = new JsonArray();
      for (MessagingModule messagingModule : messagingModules) {
        query.append(TABLE_COLUMNS_PLACEHOLDER);
        prepareInsertQueryParameters(messagingModule, params);
      }
      String preparedQuery = StringUtils.strip(query.toString(), ",");
      pgClientFactory.getInstance().execute(sqlConnection, preparedQuery, params, promise);
    } catch (Exception e) {
      LOGGER.error("Error saving Messaging Modules", e);
     promise.fail(e);
    }
    return promise.future().map(updateResult -> messagingModules);
  }

  private void prepareInsertQueryParameters(MessagingModule messagingModule, JsonArray queryParams) {
    queryParams.add(messagingModule.getId())
      .add(messagingModule.getEventType())
      .add(messagingModule.getModuleId())
      .add(messagingModule.getTenantId())
      .add(messagingModule.getModuleRole().value())
      .add(messagingModule.getActivated());
    String subscriberCallback = messagingModule.getSubscriberCallback();
    queryParams.add(subscriberCallback != null ? subscriberCallback : EMPTY);
  }

  @Override
  public Future<Boolean> delete(String id) {
    Promise<UpdateResult> promise = Promise.promise();
    String query = format(DELETE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
    JsonArray params = new JsonArray().add(id);
    pgClientFactory.getInstance().execute(query, params, promise);
    return promise.future().map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<Boolean> delete(MessagingModuleFilter filter) {
    Promise<UpdateResult> promise = Promise.promise();
    String query = format(DELETE_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter));
    pgClientFactory.getInstance().execute(query, promise);
    return promise.future().map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<List<MessagingModule>> getAll() {
    Promise<ResultSet> promise = Promise.promise();
    String preparedQuery = format(GET_ALL_SQL, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().select(preparedQuery, promise);
    return promise.future().map(this::mapResultSetToMessagingModuleList);
  }

  private Future<Boolean> delete(MessagingModuleFilter filter, AsyncResult<SQLConnection> sqlConnection) {
    Promise<UpdateResult> promise = Promise.promise();
    String query = format(DELETE_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter));
    pgClientFactory.getInstance().execute(sqlConnection, query, promise);
    return promise.future().map(updateResult -> updateResult.getUpdated() == 1);
  }

  private MessagingModule mapRowJsonToMessagingModule(JsonObject rowAsJson) {
    return new MessagingModule()
      .withId(rowAsJson.getString("id"))
      .withEventType(rowAsJson.getString("event_type_id"))
      .withModuleId(rowAsJson.getString("module_id"))
      .withTenantId(rowAsJson.getString("tenant_id"))
      .withModuleRole(ModuleRole.valueOf(rowAsJson.getString("role")))
      .withActivated(rowAsJson.getBoolean("activated"))
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
      conditionBuilder.append(" AND module_id LIKE '").append(filter.getModuleId()
        .replaceAll("\\d", "%")).append("'");
    }
    if (filter.getTenantId() != null) {
      conditionBuilder.append(" AND tenant_id = '").append(filter.getTenantId()).append("'");
    }
    if (filter.getModuleRole() != null) {
      conditionBuilder.append(" AND role = '").append(filter.getModuleRole().value()).append("'");
    }
    if (filter.getActivated() != null) {
      conditionBuilder.append(" AND activated = '").append(filter.getActivated()).append("'");
    }
    if (filter.getSubscriberCallback() != null) {
      conditionBuilder.append(" AND subscriber_callback = '").append(filter.getSubscriberCallback()).append("'");
    }
    return conditionBuilder.toString();
  }
}
