package org.folio.dao;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.EventDescriptor;

import java.util.List;
import java.util.Optional;

/**
 * Event Descriptor data access object
 */
public interface EventDescriptorDao {

  /**
   * Searches for all {@link EventDescriptor} in database
   *
   * @return future with EventDescriptor list
   */
  Future<List<EventDescriptor>> getAll();

  /**
   * Searches {@link EventDescriptor} by id
   *
   * @param id eventDescriptor id
   * @return future with optional of EventDescriptor
   */
  Future<Optional<EventDescriptor>> getById(String id);

  /**
   * Searches {@link EventDescriptor} entities by event types
   *
   * @param eventTypes event types list
   * @return future with EventDescriptor list
   */
  Future<List<EventDescriptor>> getByEventTypes(List<String> eventTypes);

  /**
   * Saves new {@link EventDescriptor} to data base
   *
   * @param eventDescriptor eventDescriptor entity to save
   * @return eventDescriptor id
   */
  Future<String> save(EventDescriptor eventDescriptor);

  /**
   * Updates {@link EventDescriptor} in data base
   *
   *
   * @param eventDescriptor entity to update
   * @return future with updated eventDescriptor
   */
  Future<EventDescriptor> update(EventDescriptor eventDescriptor);

  /**
   * Deletes {@link EventDescriptor} by id
   *
   * @param id eventDescriptor id
   * @return future with boolean
   */
  Future<Boolean> delete(String id);
}
