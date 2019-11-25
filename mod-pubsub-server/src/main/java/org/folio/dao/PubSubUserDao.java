package org.folio.dao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Pub-Sub User DAO interface
 */
public interface PubSubUserDao {

  /**
   * Save JWT token for pub-sub user in the db
   *
   * @param token    token to save
   * @param tenantId tenant id
   * @return future with true if succeeded
   */
  Future<Boolean> savePubSubJWTToken(String token, String tenantId);

  /**
   * Retrieve JWT token for pub-sub user from the db
   *
   * @param tenantId tenant id
   * @return future with token
   */
  Future<String> getPubSubJWTToken(String tenantId);

  /**
   * Get pub-sub user credentials
   *
   * @param tenantId tenant id
   * @return future with username and password in json representation
   */
  Future<JsonObject> getPubSubUserCredentials(String tenantId);

}
