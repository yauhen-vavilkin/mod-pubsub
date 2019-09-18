package org.folio.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.ext.sql.SQLConnection;
import org.folio.rest.jaxrs.model.Module;

import java.util.List;
import java.util.Optional;

/**
 * Module entity data access object
 */
public interface ModuleDao {

  /**
   * Searches for all {@link Module} in database
   *
   * @return return future with Module list
   */
  Future<List<Module>> getAll();

  /**
   * Searches {@link Module} by id
   *
   * @param id Module id
   * @return future with optional of Module
   */
  Future<Optional<Module>> getById(String id);

  /**
   * Searches {@link Module} by name using specified connection
   *
   * @param name Module name
   * @param sqlConnection connection
   * @return future with optional of Module
   */
  Future<Optional<Module>> getByName(String name, AsyncResult<SQLConnection> sqlConnection);

  /**
   * Saves new {@link Module} to data base
   *
   * @param module Module entity to save
   * @return module id
   */
  Future<String> save(Module module);

  /**
   * Saves new {@link Module} to data base using specified connection
   *
   * @param module Module entity to save
   * @param sqlConnection connection
   * @return module id
   */
  Future<String> save(Module module, AsyncResult<SQLConnection> sqlConnection);

  /**
   * Updates {@link Module} in data base
   *
   * @param id module id
   * @param module entity to update
   * @return future with updated Module
   */
  Future<Module> update(String id, Module module);

  /**
   * Deletes {@link Module} by id
   *
   * @param id Module id
   * @return future with boolean
   */
  Future<Boolean> delete(String id);
}
