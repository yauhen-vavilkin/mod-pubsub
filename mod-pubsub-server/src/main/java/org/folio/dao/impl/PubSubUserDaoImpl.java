package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.PubSubUserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static java.lang.String.format;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

@Repository
public class PubSubUserDaoImpl implements PubSubUserDao {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final String USER_TABLE_NAME = "user";
  private static final String USERNAME = "'pub-sub'";
  private static final String SET_JWT_TOKEN = "UPDATE %s.%s SET token = ? WHERE username = " + USERNAME;
  private static final String GET_JWT_TOKEN = "SELECT token FROM %s.%s WHERE username = " + USERNAME;
  private static final String GET_CREDENTIALS = "SELECT username, password FROM %s.%s";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<Boolean> savePubSubJWTToken(String token, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(SET_JWT_TOKEN, convertToPsqlStandard(tenantId), USER_TABLE_NAME);
      pgClientFactory.getInstance(tenantId).execute(query, Tuple.of(token), promise);
    } catch (Exception e) {
      LOGGER.error("Error saving token for pub-sub user", e);
      promise.fail(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<String> getPubSubJWTToken(String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(GET_JWT_TOKEN, convertToPsqlStandard(tenantId), USER_TABLE_NAME);
      pgClientFactory.getInstance(tenantId).select(query, promise);
    } catch (Exception e) {
      LOGGER.error("Error retrieving token for pub-sub user", e);
      promise.fail(e);
    }
    return promise.future().map(resultSet -> resultSet.iterator().next().getString("token"));
  }

  @Override
  public Future<JsonObject> getPubSubUserCredentials(String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(GET_CREDENTIALS, convertToPsqlStandard(tenantId), USER_TABLE_NAME);
      pgClientFactory.getInstance(tenantId).select(query, promise);
    } catch (Exception e) {
      LOGGER.error("Error retrieving pub-sub user credentials", e);
      promise.fail(e);
    }
    return promise.future().map(this::mapToUserCredentialsJson);
  }

  private JsonObject mapToUserCredentialsJson(RowSet<Row> rowSet) {
    JsonObject json = new JsonObject();
    rowSet.forEach(row -> json
      .put("username", row.getString("username"))
      .put("password", row.getString("password")));
    return json;
  }

}
