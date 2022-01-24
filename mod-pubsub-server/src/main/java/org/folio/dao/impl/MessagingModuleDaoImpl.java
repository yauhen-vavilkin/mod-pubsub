package org.folio.dao.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.MessagingModuleDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.util.DbUtil;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.util.MessagingModuleFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Implementation for the MessagingModuleDao, works with PostgresClient to access data.
 *
 * @see MessagingModuleDao
 */
@Repository
public class MessagingModuleDaoImpl implements MessagingModuleDao {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final String TABLE_NAME = "messaging_module";
  private static final String MODULE_SCHEMA = "pubsub_config";
  private static final String GET_BY_SQL = "SELECT * FROM %s.%s %s";
  private static final String INSERT_BATCH_SQL = "INSERT INTO %s.%s (id, event_type_id, module_id, tenant_id, role, activated, subscriber_callback) VALUES ($1, $2, $3, $4, $5, $6, $7)";
  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s.%s WHERE id = $1";
  private static final String DELETE_BY_SQL = "DELETE FROM %s.%s %s";
  private static final String GET_ALL_SQL = "SELECT * FROM %s.%s";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<MessagingModule>> get(MessagingModuleFilter filter) {
    Promise<RowSet<Row>> promise = Promise.promise();
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
    Promise<List<RowSet<Row>>> promise = Promise.promise();
    try {
      String query = format(INSERT_BATCH_SQL, MODULE_SCHEMA, TABLE_NAME);
      List<Tuple> params = new ArrayList<>();
      for (MessagingModule messagingModule : messagingModules) {
        params.add(prepareInsertQueryParameters(messagingModule));
      }
      pgClientFactory.getInstance().execute(sqlConnection, query, params, promise);
    } catch (Exception e) {
      LOGGER.error("Error saving Messaging Modules", e);
      promise.fail(e);
    }
    return promise.future().map(updateResult -> messagingModules);
  }

  private Tuple prepareInsertQueryParameters(MessagingModule messagingModule) {
    return Tuple.of(UUID.fromString(messagingModule.getId()),
      messagingModule.getEventType(),
      messagingModule.getModuleId(),
      messagingModule.getTenantId(),
      messagingModule.getModuleRole().value(),
      messagingModule.getActivated(),
      messagingModule.getSubscriberCallback() != null ? messagingModule.getSubscriberCallback() : EMPTY);
  }

  @Override
  public Future<Void> delete(MessagingModuleFilter filter) {
    String query = format(DELETE_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter));
    return pgClientFactory.getInstance().execute(query).mapEmpty();
  }

  @Override
  public Future<List<MessagingModule>> getAll() {
    Promise<RowSet<Row>> promise = Promise.promise();
    String preparedQuery = format(GET_ALL_SQL, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().select(preparedQuery, promise);
    return promise.future().map(this::mapResultSetToMessagingModuleList);
  }

  private Future<Boolean> delete(MessagingModuleFilter filter, AsyncResult<SQLConnection> sqlConnection) {
    Promise<RowSet<Row>> promise = Promise.promise();
    String query = format(DELETE_BY_SQL, MODULE_SCHEMA, TABLE_NAME, buildWhereClause(filter));
    pgClientFactory.getInstance().execute(sqlConnection, query, promise);
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  private MessagingModule mapRowJsonToMessagingModule(Row row) {
    return new MessagingModule()
      .withId(row.getValue("id").toString())
      .withEventType(row.getValue("event_type_id").toString())
      .withModuleId(row.getValue("module_id").toString())
      .withTenantId(row.getValue("tenant_id").toString())
      .withModuleRole(ModuleRole.valueOf(row.getString("role")))
      .withActivated(row.getBoolean("activated"))
      .withSubscriberCallback(row.getString("subscriber_callback"));
  }

  private List<MessagingModule> mapResultSetToMessagingModuleList(RowSet<Row> resultSet) {
    return Stream.generate(resultSet.iterator()::next)
      .limit(resultSet.size())
      .map(this::mapRowJsonToMessagingModule)
      .collect(Collectors.toList());
  }

  private String buildWhereClause(MessagingModuleFilter filter) {
    StringBuilder conditionBuilder = new StringBuilder("WHERE TRUE");
    if (filter.getEventType() != null) {
      conditionBuilder.append(" AND event_type_id = '").append(filter.getEventType()).append("'");
    }
    if (filter.getModuleId() != null) {
      conditionBuilder.append(" AND module_id ~ '")
        .append(buildRegexPatternForModuleId(filter))
        .append("'");
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

  private static String buildRegexPatternForModuleId(MessagingModuleFilter filter) {
    return filter.getModuleId()
      .replaceAll("\\d+", "\\\\d+")
      .replace(".", "\\."); // escape version separators to avoid matching any character
  }
}
