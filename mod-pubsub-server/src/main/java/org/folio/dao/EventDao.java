package org.folio.dao;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Event;

import java.util.Optional;

/**
 * Event data access object
 */
public interface EventDao {
  /**
   * Returns event by
   *
   * @param eventId  event id
   * @param tenantId tenant id
   * @return event entity
   */
  Future<Optional<Event>> getById(String eventId, String tenantId);
}
