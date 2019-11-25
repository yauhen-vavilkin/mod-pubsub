package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.PubSubUserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static java.lang.String.format;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

@Repository
public class PubSubUserDaoImpl implements PubSubUserDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(PubSubUserDaoImpl.class);

  private static final String USER_TABLE_NAME = "user";
  private static final String USERNAME = "'pub-sub'";
  private static final String SET_JWT_TOKEN = "UPDATE %s.%s SET token = ? WHERE username = " + USERNAME;
  private static final String GET_JWT_TOKEN = "SELECT token FROM %s.%s WHERE username = " + USERNAME;
  private static final String GET_CREDENTIALS = "SELECT username, password FROM %s.%s";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<Boolean> savePubSubJWTToken(String token, String tenantId) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(SET_JWT_TOKEN, convertToPsqlStandard(tenantId), USER_TABLE_NAME);
      JsonArray params = new JsonArray()
        .add(token);
      pgClientFactory.getInstance(tenantId).execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error saving token for pub-sub user", e);
      future.fail(e);
    }
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  @Override
  public Future<String> getPubSubJWTToken(String tenantId) {
    Future<ResultSet> future = Future.future();
    try {
      String query = format(GET_JWT_TOKEN, convertToPsqlStandard(tenantId), USER_TABLE_NAME);
      pgClientFactory.getInstance(tenantId).select(query, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error retrieving token for pub-sub user", e);
      future.fail(e);
    }
    return future.map(resultSet -> resultSet.getRows().get(0).getString("token"));
  }

  @Override
  public Future<JsonObject> getPubSubUserCredentials(String tenantId) {
    Future<ResultSet> future = Future.future();
    try {
      String query = format(GET_CREDENTIALS, convertToPsqlStandard(tenantId), USER_TABLE_NAME);
      pgClientFactory.getInstance(tenantId).select(query, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error retrieving pub-sub user credentials", e);
      future.fail(e);
    }
    return future.map(resultSet -> resultSet.getRows().get(0));
  }
}
