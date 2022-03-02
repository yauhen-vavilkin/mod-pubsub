package org.folio.services;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.EventDescriptorCollection;

import java.util.Optional;

/**
 * Event Descriptor service
 */
public interface EventDescriptorService {

  /**
   * Searches for all {@link EventDescriptor}
   *
   * @return future with EventDescriptorCollection
   */
  Future<EventDescriptorCollection> getAll();

  /**
   * Searches {@link EventDescriptor} by eventType
   *
   * @param eventType eventDescriptor eventType
   * @return future with optional of EventDescriptor
   */
  Future<Optional<EventDescriptor>> getByEventType(String eventType);

  /**
   * Saves new {@link EventDescriptor}
   *
   * @param eventDescriptor eventDescriptor entity to save
   * @return eventDescriptor event type
   */
  Future<String> save(EventDescriptor eventDescriptor);

  /**
   * Updates {@link EventDescriptor}
   *
   * @param eventDescriptor entity to update
   * @return future with updated eventDescriptor
   */
  Future<EventDescriptor> update(EventDescriptor eventDescriptor);

  /**
   * Deletes {@link EventDescriptor} by event type
   *
   * @param eventType eventDescriptor eventType
   * @return succeeded future if exactly one row was updated, failed future otherwise
   */
  Future<Void> delete(String eventType);
}
