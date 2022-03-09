package org.folio.services.publish;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Event;

/**
 * Publishing Service Interface
 */
public interface PublishingService { //NOSONAR

  /**
   * Publishes an event to an appropriate topic
   *
   * @param event    {@link Event} to send
   * @param tenantId tenant id
   * @return succeeded future if event is published, failed future otherwise
   */
  Future<Void> sendEvent(Event event, String tenantId);

}
