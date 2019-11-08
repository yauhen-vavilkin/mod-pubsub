package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import org.folio.dao.EventDescriptorDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.rest.persist.PostgresClient.pojo2json;

/**
 * Implementation for the EventDescriptorDao, works with PostgresClient to access data.
 *
 * @see EventDescriptorDao
 */
@Repository
public class EventDescriptorDaoImpl implements EventDescriptorDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventDescriptorDaoImpl.class);

  private static final String TABLE_NAME = "event_descriptor";
  private static final String MODULE_SCHEMA = "pubsub_config";
  private static final String GET_ALL_SQL = "SELECT * FROM %s.%s";
  private static final String GET_BY_ID_SQL = "SELECT * FROM %s.%s WHERE id = ?";
  private static final String INSERT_SQL = "INSERT INTO %s.%s (id, descriptor) VALUES (?, ?)";
  private static final String UPDATE_BY_ID_SQL = "UPDATE %s.%s SET descriptor = ? WHERE id = ?";
  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s.%s WHERE id = ?";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<EventDescriptor>> getAll() {
    Future<ResultSet> future = Future.future();
    String preparedQuery = format(GET_ALL_SQL, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().select(preparedQuery, future.completer());
    return future.map(this::mapResultSetToEventDescriptorList);
  }

  @Override
  public Future<Optional<EventDescriptor>> getByEventType(String eventType) {
    Future<ResultSet> future = Future.future();
    try {
      String preparedQuery = format(GET_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
      JsonArray params = new JsonArray().add(eventType);
      pgClientFactory.getInstance().select(preparedQuery, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error getting EventDescriptor by event type '{}'", e, eventType);
      future.fail(e);
    }
    return future.map(resultSet -> resultSet.getResults().isEmpty() ? Optional.empty()
      : Optional.of(mapRowJsonToEventDescriptor(resultSet.getRows().get(0))));
  }

  @Override
  public Future<List<EventDescriptor>> getByEventTypes(List<String> eventTypes) {
    Future<ResultSet> future = Future.future();
    String query = getQueryByEventTypes(eventTypes);
    String preparedQuery = format(query, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().select(preparedQuery, future.completer());
    return future.map(this::mapResultSetToEventDescriptorList);
  }

  private String getQueryByEventTypes(List<String> eventTypes) {
    StringBuilder query = new StringBuilder(GET_ALL_SQL);
    if (!isEmpty(eventTypes)) {
      String conditionByEventTypes = eventTypes.stream()
        .map(eventType -> new StringBuilder("'").append(eventType).append("'"))
        .collect(Collectors.joining(", ", "id IN (", ")"));

      query.append( " WHERE ").append(conditionByEventTypes);
    }
    return query.toString();
  }

  @Override
  public Future<String> save(EventDescriptor eventDescriptor) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(INSERT_SQL, MODULE_SCHEMA, TABLE_NAME);
      JsonArray params = new JsonArray()
        .add(eventDescriptor.getEventType())
        .add(pojo2json(eventDescriptor));
      pgClientFactory.getInstance().execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error saving EventDescriptor with event type '{}'", e, eventDescriptor.getEventType());
      future.fail(e);
    }
    return future.map(updateResult -> eventDescriptor.getEventType());
  }

  @Override
  public Future<EventDescriptor> update(EventDescriptor eventDescriptor) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(UPDATE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
      JsonArray params = new JsonArray()
        .add(pojo2json(eventDescriptor))
        .add(eventDescriptor.getEventType());
      pgClientFactory.getInstance().execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error updating EventDescriptor by event type '{}'", e, eventDescriptor.getEventType());
      future.fail(e);
    }
    return future.compose(updateResult -> updateResult.getUpdated() == 1
      ? Future.succeededFuture(eventDescriptor)
      : Future.failedFuture(new NotFoundException(format("EventDescriptor with event type '%s' was not updated", eventDescriptor.getEventType()))));
  }

  @Override
  public Future<Boolean> delete(String eventType) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(DELETE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
      JsonArray params = new JsonArray().add(eventType);
      pgClientFactory.getInstance().execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error deleting EventDescriptor with event type '{}'", e, eventType);
      future.fail(e);
    }
    return future.map(updateResult -> updateResult.getUpdated() == 1);
  }

  private EventDescriptor mapRowJsonToEventDescriptor(JsonObject rowAsJson) {
    EventDescriptor eventDescriptor = new EventDescriptor();
    eventDescriptor.setEventType(rowAsJson.getString("id"));
    JsonObject descriptorAsJson = new JsonObject(rowAsJson.getString("descriptor"));
    eventDescriptor.setDescription(descriptorAsJson.getString("description"));
    eventDescriptor.setEventTTL(descriptorAsJson.getInteger("eventTTL"));
    eventDescriptor.setSigned(descriptorAsJson.getBoolean("signed"));
    return eventDescriptor;
  }

  private List<EventDescriptor> mapResultSetToEventDescriptorList(ResultSet resultSet) {
    return resultSet.getRows().stream()
      .map(this::mapRowJsonToEventDescriptor)
      .collect(Collectors.toList());
  }
}
