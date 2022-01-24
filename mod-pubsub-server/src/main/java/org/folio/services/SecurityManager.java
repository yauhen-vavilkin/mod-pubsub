package org.folio.services;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;


/**
 * Security Manager Interface
 */
public interface SecurityManager {

  /**
   * Get JWT token, log in system user if needed
   *
   * @param params okapi connection params
   * @return future with token
   */
  Future<String> getJWTToken(OkapiConnectionParams params);

  /**
   * Creates new system user if it doesn't exist and assigns all necessary permissions
   *
   * @param params okapi connection params
   */
  Future<Void> createPubSubUser(OkapiConnectionParams params);
}
