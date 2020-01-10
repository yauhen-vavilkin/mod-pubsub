package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import org.folio.dao.AuditMessageDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.AuditMessagePayload;
import org.folio.rest.util.AuditMessageFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
  private static final String INSERT_AUDIT_MESSAGE_QUERY = "INSERT INTO %s.%s (id, event_id, event_type, tenant_id, audit_date, state, published_by, correlation_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
  private static final String INSERT_AUDIT_MESSAGE_PAYLOAD_QUERY = "INSERT INTO %s.%s (event_id, content) VALUES (?, ?);";
  private static final String SELECT_QUERY = "SELECT * FROM %s.%s";
  private static final String GET_BY_EVENT_ID_QUERY = "SELECT * FROM %s.%s WHERE event_id = ?;";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<AuditMessage>> getAuditMessages(AuditMessageFilter filter, String tenantId) {
    Promise<ResultSet> promise = Promise.promise();
    try {
      String query = format(SELECT_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_TABLE)
        .concat(constructWhereClauseForGetAuditMessagesQuery(filter));
      pgClientFactory.getInstance(tenantId).select(query, promise);
    } catch (Exception e) {
      LOGGER.error("Error retrieving audit messages", e);
      promise.fail(e);
    }
    return promise.future().map(this::mapAuditMessagesResult);
  }

  @Override
  public Future<AuditMessage> saveAuditMessage(AuditMessage auditMessage) {
    Promise<UpdateResult> promise = Promise.promise();
    try {
      String query = format(INSERT_AUDIT_MESSAGE_QUERY, convertToPsqlStandard(auditMessage.getTenantId()), AUDIT_MESSAGE_TABLE);
      JsonArray params = new JsonArray()
        .add(auditMessage.getId())
        .add(auditMessage.getEventId())
        .add(auditMessage.getEventType())
        .add(auditMessage.getTenantId())
        .add(Timestamp.from(auditMessage.getAuditDate().toInstant()).toString())
        .add(auditMessage.getState())
        .add(auditMessage.getPublishedBy())
        .add(auditMessage.getCorrelationId() != null ? auditMessage.getCorrelationId() : "")
        .add(auditMessage.getCreatedBy() != null ? auditMessage.getCreatedBy() : "");
      pgClientFactory.getInstance(auditMessage.getTenantId()).execute(query, params, promise);
    } catch (Exception e) {
      LOGGER.error("Error saving audit message with id {}", e, auditMessage.getId());
      promise.fail(e);
    }
    return promise.future().map(updateResult -> auditMessage);
  }

  @Override
  public Future<AuditMessagePayload> saveAuditMessagePayload(AuditMessagePayload auditMessagePayload, String tenantId) {
    Promise<UpdateResult> promise = Promise.promise();
    try {
      String query = format(INSERT_AUDIT_MESSAGE_PAYLOAD_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_PAYLOAD_TABLE);
      JsonArray params = new JsonArray()
        .add(auditMessagePayload.getEventId())
        .add(pojo2json(auditMessagePayload));
      pgClientFactory.getInstance(tenantId).execute(query, params, promise);
    } catch (Exception e) {
      LOGGER.error("Error saving audit message payload for event with id {}", e, auditMessagePayload.getEventId());
      promise.fail(e);
    }
    return promise.future().map(updateResult -> auditMessagePayload);

  }

  @Override
  public Future<Optional<AuditMessagePayload>> getAuditMessagePayloadByEventId(String eventId, String tenantId) {
    Promise<ResultSet> promise = Promise.promise();
    try {
      String query = format(GET_BY_EVENT_ID_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_PAYLOAD_TABLE);
      JsonArray params = new JsonArray().add(eventId);
      pgClientFactory.getInstance(tenantId).select(query, params, promise);
    } catch (Exception e) {
      LOGGER.error("Error while searching for audit message payload by event id {}", e, eventId);
      promise.fail(e);
    }
    return promise.future().map(resultSet -> resultSet.getResults().isEmpty()
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
      .withPublishedBy(result.getString("published_by"))
      .withAuditDate(Date.from(LocalDateTime.parse(result.getString("audit_date")).toInstant(ZoneOffset.UTC)))
      .withState(AuditMessage.State.fromValue(result.getString("state")));
  }

  private AuditMessagePayload mapAuditMessagePayload(JsonObject result) {
    return new AuditMessagePayload()
      .withEventId(result.getString("event_id"))
      .withContent(result.getString("content"));
  }

  private String constructWhereClauseForGetAuditMessagesQuery(AuditMessageFilter filter) {
    StringBuilder whereClause = new StringBuilder(" WHERE ");
    whereClause.append("audit_date BETWEEN ")
      .append("'").append(Timestamp.from(filter.getStartDate().toInstant())).append("' AND ")
      .append("'").append(Timestamp.from(filter.getEndDate().toInstant())).append("'");
    if (filter.getEventId() != null) {
      whereClause.append(" AND event_id = '").append(filter.getEventId()).append("'");
    }
    if (filter.getEventType() != null) {
      whereClause.append(" AND event_type = '").append(filter.getEventType()).append("'");
    }
    if (filter.getCorrelationId() != null) {
      whereClause.append(" AND correlation_id = '").append(filter.getCorrelationId()).append("'");
    }
    return whereClause.append(";").toString();
  }
}
