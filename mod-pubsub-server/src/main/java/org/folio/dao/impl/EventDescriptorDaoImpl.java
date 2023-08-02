package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.EventDescriptorDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

/**
 * Implementation for the EventDescriptorDao, works with PostgresClient to access data.
 *
 * @see EventDescriptorDao
 */
@Repository
public class EventDescriptorDaoImpl implements EventDescriptorDao {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final String TABLE_NAME = "event_descriptor";
  private static final String MODULE_SCHEMA = "pubsub_config";
  private static final String GET_ALL_SQL = "SELECT * FROM %s.%s";
  private static final String GET_BY_ID_SQL = "SELECT * FROM %s.%s WHERE id = $1";
  private static final String INSERT_SQL = "INSERT INTO %s.%s (id, descriptor) VALUES ($1, $2)";
  private static final String UPDATE_BY_ID_SQL = "UPDATE %s.%s SET descriptor = $1 WHERE id = $2";
  private static final String DELETE_BY_ID_SQL = "DELETE FROM %s.%s WHERE id = $1";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<EventDescriptor>> getAll() {
    Promise<RowSet<Row>> promise = Promise.promise();
    String preparedQuery = format(GET_ALL_SQL, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().selectRead(preparedQuery, 0, promise);
    return promise.future().map(this::mapResultSetToEventDescriptorList);
  }

  @Override
  public Future<Optional<EventDescriptor>> getByEventType(String eventType) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String preparedQuery = format(GET_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
      pgClientFactory.getInstance().selectRead(preparedQuery, Tuple.of(eventType), promise);
    } catch (Exception e) {
      LOGGER.error("Error getting EventDescriptor by event type '{}'", eventType, e);
      promise.fail(e);
    }
    return promise.future().map(resultSet -> resultSet.rowCount() == 0 ? Optional.empty()
      : Optional.of(mapRowJsonToEventDescriptor(resultSet.iterator().next())));
  }

  @Override
  public Future<List<EventDescriptor>> getByEventTypes(List<String> eventTypes) {
    Promise<RowSet<Row>> promise = Promise.promise();
    String query = getQueryByEventTypes(eventTypes);
    String preparedQuery = format(query, MODULE_SCHEMA, TABLE_NAME);
    pgClientFactory.getInstance().selectRead(preparedQuery, 0, promise);
    return promise.future().map(this::mapResultSetToEventDescriptorList);
  }

  private String getQueryByEventTypes(List<String> eventTypes) {
    StringBuilder query = new StringBuilder(GET_ALL_SQL);
    if (!isEmpty(eventTypes)) {
      String conditionByEventTypes = eventTypes.stream()
        .map(eventType -> new StringBuilder("'").append(eventType).append("'"))
        .collect(Collectors.joining(", ", "id IN (", ")"));

      query.append(" WHERE ").append(conditionByEventTypes);
    }
    return query.toString();
  }

  @Override
  public Future<String> save(EventDescriptor eventDescriptor) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(INSERT_SQL, MODULE_SCHEMA, TABLE_NAME);
      pgClientFactory.getInstance().execute(query, Tuple.of(eventDescriptor.getEventType(), JsonObject.mapFrom(eventDescriptor)),
        promise);
    } catch (Exception e) {
      LOGGER.error("Error saving EventDescriptor with event type '{}'", eventDescriptor.getEventType(), e);
      promise.fail(e);
    }
    return promise.future().map(updateResult -> eventDescriptor.getEventType());
  }

  @Override
  public Future<EventDescriptor> update(EventDescriptor eventDescriptor) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(UPDATE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
      pgClientFactory.getInstance().execute(query, Tuple.of(JsonObject.mapFrom((eventDescriptor)), eventDescriptor.getEventType()),
        promise);
    } catch (Exception e) {
      LOGGER.error("Error updating EventDescriptor by event type '{}'", eventDescriptor.getEventType(), e);
      promise.fail(e);
    }
    return promise.future().compose(updateResult -> updateResult.rowCount() == 1
      ? Future.succeededFuture(eventDescriptor)
      : Future.failedFuture(new NotFoundException(format("EventDescriptor with event type '%s' was not updated", eventDescriptor.getEventType()))));
  }

  @Override
  public Future<Void> delete(String eventType) {
    String query = format(DELETE_BY_ID_SQL, MODULE_SCHEMA, TABLE_NAME);
    return pgClientFactory.getInstance().execute(query, Tuple.of(eventType))
      .flatMap(result -> {
        if (result.rowCount() == 1) {
          return Future.succeededFuture();
        }
        String message = format("Error deleting EventDescriptor with event type '%s'", eventType);
        NotFoundException notFoundException = new NotFoundException(message);
        LOGGER.error(message, notFoundException);
        return Future.failedFuture(notFoundException);
      });
  }

  private EventDescriptor mapRowJsonToEventDescriptor(Row rowAsJson) {
    EventDescriptor eventDescriptor = new EventDescriptor();
    eventDescriptor.setEventType(rowAsJson.getString("id"));
    JsonObject descriptorAsJson = new JsonObject(rowAsJson.getValue("descriptor").toString());
    eventDescriptor.setDescription(descriptorAsJson.getString("description"));
    eventDescriptor.setEventTTL(descriptorAsJson.getInteger("eventTTL"));
    eventDescriptor.setSigned(descriptorAsJson.getBoolean("signed"));
    eventDescriptor.setTmp(descriptorAsJson.getBoolean("tmp"));
    return eventDescriptor;
  }

  private List<EventDescriptor> mapResultSetToEventDescriptorList(RowSet<Row> resultSet) {
    return Stream.generate(resultSet.iterator()::next)
      .limit(resultSet.size())
      .map(this::mapRowJsonToEventDescriptor)
      .collect(Collectors.toList());
  }
}
