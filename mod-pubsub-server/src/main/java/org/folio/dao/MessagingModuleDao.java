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
   * Saves in transaction list of {@link MessagingModule} to data base with specified moduleName
   *
   * @param moduleName  module name
   * @param messagingModules list of Messaging Module entities
   * @return future with list of created Messaging Module entities
   */
  Future<List<MessagingModule>> save(String moduleName, List<MessagingModule> messagingModules);

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

  /**
   * Deletes {@link MessagingModule} by module name and filter
   *
   * @param moduleName module name
   * @param filter messagingModule filter
   * @return future with boolean
   */
  Future<Boolean> deleteByModuleNameAndFilter(String moduleName, MessagingModuleFilter filter);
}
