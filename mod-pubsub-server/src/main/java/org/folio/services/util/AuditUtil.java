package org.folio.services.util;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.AuditMessagePayload;
import org.folio.rest.jaxrs.model.Event;

import java.util.Date;
import java.util.UUID;

public final class AuditUtil {

  private AuditUtil() {
  }

  public static JsonObject constructJsonAuditMessagePayload(Event event) {
    return JsonObject.mapFrom(new AuditMessagePayload()
      .withEventId(event.getId())
      .withContent(event.getEventPayload()));
  }

  public static JsonObject constructJsonAuditMessage(Event event, String tenantId, AuditMessage.State state) {
    return JsonObject.mapFrom(new AuditMessage()
      .withId(UUID.randomUUID().toString())
      .withEventId(event.getId())
      .withEventType(event.getEventType())
      .withTenantId(tenantId)
      .withCorrelationId(event.getEventMetadata().getCorrelationId())
      .withCreatedBy(event.getEventMetadata().getCreatedBy())
      .withPublishedBy(event.getEventMetadata().getPublishedBy())
      .withAuditDate(new Date())
      .withState(state));
  }

}
