package org.folio.dao;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.MessagingModuleFilter;

import java.util.List;

/**
 * Messaging module data access object
 */
public interface MessagingModuleDao {

  /**
   * Searches for {@link MessagingModule} entities by filter in database
   *
   * @param filter messagingModule filter
   * @return return future with MessagingModule list
   */
  Future<List<MessagingModule>> get(MessagingModuleFilter filter);

  /**
   * Saves in transaction list of {@link MessagingModule} to data base with specified moduleName
   * Deletes previous messaging modules with same module name, event type and tenant id as in specified messagingModules
   *
   * @param messagingModules list of Messaging Module entities
   * @return future with list of created Messaging Module entities
   */
  Future<List<MessagingModule>> save(List<MessagingModule> messagingModules);

  /**
   * Deletes {@link MessagingModule} matching filter criteria
   *
   * @param filter messagingModule filter
   * @return succeeded Future if deleted or not found
   */
  Future<Void> delete(MessagingModuleFilter filter);

  /**
   * Gets all registered {@link MessagingModule}
   *
   * @return list of Messaging Modules
   */
  Future<List<MessagingModule>> getAll();
}
