package org.folio.services;

import io.vertx.core.Future;
import org.folio.rest.util.OkapiConnectionParams;

import java.util.List;

/**
 * Subscriber interface
 */
public interface ConsumerService {

  /**
   * Creates a consumer and subscribes it to particular topics
   *
   * @param eventTypes list of event types that specified module is subscribing to receive
   * @param params     Okapi connection params
   * @return future with true if succeeded
   */
  Future<Boolean> subscribe(List<String> eventTypes, OkapiConnectionParams params);
}
