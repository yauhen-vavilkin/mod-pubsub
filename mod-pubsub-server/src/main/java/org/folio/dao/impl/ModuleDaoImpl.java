package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import javassist.NotFoundException;
import org.folio.dao.ModuleDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.Module;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Implementation for the ModuleDao, works with PostgresClient to access data.
 *
 * @see ModuleDao
 */
@Repository
public class ModuleDaoImpl implements ModuleDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModuleDaoImpl.class);

  private static final String TABLE_NAME = "module";
  private static final String MODULE_SCHEMA = "pubsub_config";
  private static final String GET_ALL_SQL = "SELECT * FROM %s.%s";
  private static final String GET_BY_ID_SQL = "SELECT * FROM %s.%s WHERE id = ?";
  private static final String INSERT_SQL = "INSERT INTO %s.%s (id, name) VALUES (?, ?)";
  private static final String UPDATE_BY_ID_SQL = "UPDATE %s.%s SET name = ? WHERE id = ?";
  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s.%s WHERE id = ?";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<Module>> getAll() {
    Future<ResultSet> future = Future.future();
    String preparedQuery = format(GET_ALL_SQL, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().select(preparedQuery, future.completer());
    return future.map(this::mapResultSetToModuleList);
  }

  @Override
  public Future<Optional<Module>> getById(String id) {
    Future<ResultSet> future = Future.future();
    String preparedQuery = format(GET_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
    JsonArray params = new JsonArray().add(id);
    pgClientFactory.getInstance().select(preparedQuery, params, future.completer());
    return future.map(resultSet -> resultSet.getResults().isEmpty()
      ? Optional.empty() : Optional.of(mapRowJsonToModule(resultSet.getRows().get(0))));
  }

  @Override
  public Future<String> save(Module module) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(INSERT_SQL, MODULE_SCHEMA, TABLE_NAME);
      JsonArray params = new JsonArray()
        .add(module.getId())
        .add(module.getName());
      pgClientFactory.getInstance().execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error saving Module", e);
      future.fail(e);
    }
    return future.map(updateResult -> module.getId());
  }

  @Override
  public Future<Module> update(String id, Module module) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(UPDATE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
      JsonArray params = new JsonArray()
        .add(module.getName())
        .add(id);
      pgClientFactory.getInstance().execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error updating Module by id '{}'", e, id);
      future.fail(e);
    }
    return future.compose(updateResult -> updateResult.getUpdated() == 1 ? Future.succeededFuture(module)
      : Future.failedFuture(new NotFoundException(format("MessagingModule by id '%s' was not updated", id))));
  }

  @Override
  public Future<Boolean> delete(String id) {
    Future<UpdateResult> future = Future.future();
    String query = format(DELETE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
    JsonArray params = new JsonArray().add(id);
    pgClientFactory.getInstance().execute(query, params, future.completer());
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  private Module mapRowJsonToModule(JsonObject rowAsJson) {
    Module module = new Module();
    module.setId(rowAsJson.getString("id"));
    module.setName(rowAsJson.getString("name"));
    return module;
  }

  private List<Module> mapResultSetToModuleList(ResultSet resultSet) {
    return resultSet.getRows().stream()
      .map(this::mapRowJsonToModule)
      .collect(Collectors.toList());
  }
}
