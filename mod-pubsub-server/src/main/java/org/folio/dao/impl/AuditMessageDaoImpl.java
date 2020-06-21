package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

@Repository
public class AuditMessageDaoImpl implements AuditMessageDao {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditMessageDaoImpl.class);

  private static final String AUDIT_MESSAGE_TABLE = "audit_message";
  private static final String AUDIT_MESSAGE_PAYLOAD_TABLE = "audit_message_payload";
  private static final String INSERT_AUDIT_MESSAGE_QUERY = "INSERT INTO %s.%s (id, event_id, event_type, tenant_id, audit_date, state, published_by, correlation_id, created_by, error_message) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10);";
  private static final String INSERT_AUDIT_MESSAGE_PAYLOAD_QUERY = "INSERT INTO %s.%s (event_id, content) VALUES ($1, $2);";
  private static final String SELECT_QUERY = "SELECT * FROM %s.%s";
  private static final String GET_BY_EVENT_ID_QUERY = "SELECT * FROM %s.%s WHERE event_id = $1;";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<List<AuditMessage>> getAuditMessages(AuditMessageFilter filter, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
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
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(INSERT_AUDIT_MESSAGE_QUERY, convertToPsqlStandard(auditMessage.getTenantId()), AUDIT_MESSAGE_TABLE);
      Tuple params = Tuple.of(UUID.fromString(auditMessage.getId()),
        UUID.fromString(auditMessage.getEventId()),
        auditMessage.getEventType(),
        auditMessage.getTenantId(),
        Timestamp.from(auditMessage.getAuditDate().toInstant()).toLocalDateTime(),
        auditMessage.getState().value(),
        auditMessage.getPublishedBy(),
        auditMessage.getCorrelationId() != null ? auditMessage.getCorrelationId() : EMPTY,
        auditMessage.getCreatedBy() != null ? auditMessage.getCreatedBy() : EMPTY,
        auditMessage.getErrorMessage() != null ? auditMessage.getErrorMessage() : EMPTY);
      pgClientFactory.getInstance(auditMessage.getTenantId()).execute(query, params, promise);
    } catch (Exception e) {
      LOGGER.error("Error saving audit message with id {}", e, auditMessage.getId());
      promise.fail(e);
    }
    return promise.future().map(updateResult -> auditMessage);
  }

  @Override
  public Future<AuditMessagePayload> saveAuditMessagePayload(AuditMessagePayload auditMessagePayload, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(INSERT_AUDIT_MESSAGE_PAYLOAD_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_PAYLOAD_TABLE);
      pgClientFactory.getInstance(tenantId).execute(query, Tuple.of(UUID.fromString(auditMessagePayload.getEventId()), JsonObject.mapFrom(auditMessagePayload)),
        promise);
    } catch (Exception e) {
      LOGGER.error("Error saving audit message payload for event with id {}", e, auditMessagePayload.getEventId());
      promise.fail(e);
    }
    return promise.future().map(updateResult -> auditMessagePayload);

  }

  @Override
  public Future<Optional<AuditMessagePayload>> getAuditMessagePayloadByEventId(String eventId, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(GET_BY_EVENT_ID_QUERY, convertToPsqlStandard(tenantId), AUDIT_MESSAGE_PAYLOAD_TABLE);
      pgClientFactory.getInstance(tenantId).select(query, Tuple.of(UUID.fromString(eventId)), promise);
    } catch (Exception e) {
      LOGGER.error("Error while searching for audit message payload by event id {}", e, eventId);
      promise.fail(e);
    }
    return promise.future().map(resultSet -> resultSet.rowCount() == 0
      ? Optional.empty() : Optional.of(mapAuditMessagePayload(resultSet.iterator().next())));
  }

  private List<AuditMessage> mapAuditMessagesResult(RowSet<Row> resultSet) {
    return Stream.generate(resultSet.iterator()::next)
      .limit(resultSet.size())
      .map(this::mapAuditMessage)
      .collect(Collectors.toList());
  }

  private AuditMessage mapAuditMessage(Row row) {
    return new AuditMessage()
      .withId(row.getValue("id").toString())
      .withEventId(row.getValue("event_id").toString())
      .withEventType(row.getString("event_type"))
      .withCorrelationId(row.getString("correlation_id"))
      .withTenantId(row.getString("tenant_id"))
      .withCreatedBy(row.getString("created_by"))
      .withPublishedBy(row.getString("published_by"))
      .withAuditDate(Date.from(LocalDateTime.parse(row.getValue("audit_date").toString()).toInstant(ZoneOffset.UTC)))
      .withState(AuditMessage.State.fromValue(row.getString("state")))
      .withErrorMessage(row.getString("error_message"));
  }

  private AuditMessagePayload mapAuditMessagePayload(Row result) {
    return new AuditMessagePayload()
      .withEventId(result.getValue("event_id").toString())
      .withContent(result.getValue("content").toString());
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
