package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import org.folio.dao.AuditMessageDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.util.AuditMessageFilter;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.AuditMessagePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.rest.persist.PostgresClient.pojo2json;

@Repository
public class AuditMessageDaoImpl implements AuditMessageDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditMessageDaoImpl.class);

  private static final String AUDIT_MESSAGE_TABLE = "audit_message";
  private static final String AUDIT_MESSAGE_PAYLOAD_TABLE = "audit_message_payload";
  private static final String INSERT_AUDIT_MESSAGE_QUERY = "INSERT INTO %s.%s (id, event_id, event_type, correlation_id, tenant_id, created_by, audit_date, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
  private static final String INSERT_AUDIT_MESSAGE_PAYLOAD_QUERY = "INSERT INTO %s.%s (event_id, content) VALUES (?, ?);";
  private static final String SELECT_QUERY = "SELECT * FROM %s.%s";
  private static final String GET_BY_EVENT_ID_QUERY = "SELECT * FROM %s.%s WHERE event_id = ?;";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<AuditMessage>> getAuditMessages(AuditMessageFilter filter, String tenantId) {
    Future<ResultSet> future = Future.future();
    try {
      String query = format(SELECT_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_TABLE)
        .concat(constructWhereClauseForGetAuditMessagesQuery(filter));
      pgClientFactory.getInstance(tenantId).select(query, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error retrieving audit messages", e);
      future.fail(e);
    }
    return future.map(this::mapAuditMessagesResult);
  }

  @Override
  public Future<AuditMessage> saveAuditMessage(AuditMessage auditMessage) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(INSERT_AUDIT_MESSAGE_QUERY, convertToPsqlStandard(auditMessage.getTenantId()), AUDIT_MESSAGE_TABLE);
      JsonArray params = new JsonArray()
        .add(auditMessage.getId())
        .add(auditMessage.getEventId())
        .add(auditMessage.getEventType())
        .add(auditMessage.getTenantId())
        .add(auditMessage.getCreatedBy())
        .add(auditMessage.getAuditDate().getTime())
        .add(auditMessage.getState());
      pgClientFactory.getInstance(auditMessage.getTenantId()).execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error saving audit message with id {}", e, auditMessage.getId());
      future.fail(e);
    }
    return future.map(updateResult -> auditMessage);
  }

  @Override
  public Future<AuditMessagePayload> saveAuditMessagePayload(AuditMessagePayload auditMessagePayload, String tenantId) {
    Future<UpdateResult> future = Future.future();
    try {
      String query = format(INSERT_AUDIT_MESSAGE_PAYLOAD_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_PAYLOAD_TABLE);
      JsonArray params = new JsonArray()
        .add(auditMessagePayload.getEventId())
        .add(pojo2json(auditMessagePayload));
      pgClientFactory.getInstance(tenantId).execute(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error saving audit message payload for event with id {}", e, auditMessagePayload.getEventId());
      future.fail(e);
    }
    return future.map(updateResult -> auditMessagePayload);

  }

  @Override
  public Future<Optional<AuditMessagePayload>> getAuditMessagePayloadByEventId(String eventId, String tenantId) {
    Future<ResultSet> future = Future.future();
    try {
      String query = format(GET_BY_EVENT_ID_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_PAYLOAD_TABLE);
      JsonArray params = new JsonArray().add(eventId);
      pgClientFactory.getInstance(tenantId).select(query, params, future.completer());
    } catch (Exception e) {
      LOGGER.error("Error while searching for audit message payload by event id {}", e, eventId);
      future.fail(e);
    }
    return future.map(resultSet -> resultSet.getResults().isEmpty()
      ? Optional.empty() : Optional.of(mapAuditMessagePayload(resultSet.getRows().get(0))));
  }

  private List<AuditMessage> mapAuditMessagesResult(ResultSet resultSet) {
    return resultSet.getRows().stream().map(this::mapAuditMessage).collect(Collectors.toList());
  }

  private AuditMessage mapAuditMessage(JsonObject result) {
    return new AuditMessage()
      .withId(result.getString("id"))
      .withEventId(result.getString("event_id"))
      .withEventType(result.getString("event_type"))
      .withCorrelationId(result.getString("correlation_id"))
      .withTenantId(result.getString("tenant_id"))
      .withCreatedBy(result.getString("created_by"))
      .withAuditDate(new Date(result.getLong("audit_date")))
      .withState(AuditMessage.State.fromValue(result.getString("state")));
  }

  private AuditMessagePayload mapAuditMessagePayload(JsonObject result) {
    return new AuditMessagePayload()
      .withEventId(result.getString("event_id"))
      .withContent(result.getString("content"));
  }

  private String constructWhereClauseForGetAuditMessagesQuery(AuditMessageFilter filter) {
    StringBuilder whereClause = new StringBuilder(" WHERE ");
    whereClause.append("audit_date BETWEEN ").append(filter.getFromDate()).append(" AND ").append(filter.getTillDate());
    if (filter.getEventId() != null) {
      whereClause.append(" AND event_id = ").append(filter.getEventId());
    }
    if (filter.getEventType() != null) {
      whereClause.append(" AND event_type = ").append(filter.getEventType());
    }
    if (filter.getCorrelationId() != null) {
      whereClause.append(" AND correlation_id = ").append(filter.getCorrelationId());
    }
    return whereClause.append(";").toString();
  }
}
