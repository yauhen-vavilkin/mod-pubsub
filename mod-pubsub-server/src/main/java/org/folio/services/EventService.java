package org.folio.services;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Event;

import java.util.Optional;

/**
 * Event service
 */
public interface EventService {

  /**
   * Returns event by
   *
   * @param eventId  event id
   * @param tenantId tenant id
   * @return event entity
   */
  Future<Optional<Event>> getById(String eventId, String tenantId);
}
