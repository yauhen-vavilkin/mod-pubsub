package org.folio.dao;

import io.vertx.core.Future;
import org.folio.dao.impl.MessagingModuleFilter;
import org.folio.rest.jaxrs.model.MessagingModule;

import java.util.List;
import java.util.Optional;

/**
 * Messaging module data access object
 */
public interface MessagingModuleDao {

  /**
   * Searches for {@link MessagingModule} entities by filter in database
   *
   * @return return future with MessagingModule list
   * @param filter messagingModule filter
   */
  Future<List<MessagingModule>> get(MessagingModuleFilter filter);

  /**
   * Searches {@link MessagingModule} by id
   *
   * @param id MessagingModule id
   * @return future with optional of MessagingModule
   */
  Future<Optional<MessagingModule>> getById(String id);

  /**
   * Saves new {@link MessagingModule} to data base
   *
   * @param messagingModule messagingModule entity to save
   * @return messagingModule id
   */
  Future<String> save(MessagingModule messagingModule);

  /**
   * Updates {@link MessagingModule} in data base
   *
   * @param id messagingModule id
   * @param messagingModule entity to update
   * @return future with updated MessagingModule
   */
  Future<MessagingModule> update(String id, MessagingModule messagingModule);

  /**
   * Deletes {@link MessagingModule} by id
   *
   * @param id messagingModule id
   * @return future with boolean
   */
  Future<Boolean> delete(String id);
}
