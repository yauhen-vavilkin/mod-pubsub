package org.folio.dao.util;

import java.util.Date;

public class AuditMessageFilter {

  private final Date startDate;
  private final Date endDate;
  private String eventId;
  private String eventType;
  private String correlationId;

  public AuditMessageFilter(Date startDate, Date endDate) {
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public Date getStartDate() {
    return startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public AuditMessageFilter withEventId(String eventId) {
    this.eventId = eventId;
    return this;
  }

  public AuditMessageFilter withEventType(String eventType) {
    this.eventType = eventType;
    return this;
  }

  public AuditMessageFilter withCorrelationId(String correlationId) {
    this.correlationId = correlationId;
    return this;
  }
}
