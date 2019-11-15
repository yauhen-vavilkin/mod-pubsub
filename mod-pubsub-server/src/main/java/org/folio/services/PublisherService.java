package org.folio.services;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Event;

/**
 * Publishing Service
 */
public interface PublisherService {

  /**
   * Publishes event to the appropriate topic in kafka
   *
   * @param event    event to publish
   * @param tenantId tenant id
   * @return future with true if succeeded
   */
  Future<Boolean> publishEvent(Event event, String tenantId);
}
