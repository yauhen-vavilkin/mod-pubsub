package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.services.PublisherService;
import org.folio.services.audit.AuditService;
import org.folio.services.cache.Cache;
import org.folio.services.publish.PublishingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.logging.log4j.util.Strings.isNotEmpty;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.services.util.AuditUtil.constructJsonAuditMessage;
import static org.folio.services.util.AuditUtil.constructJsonAuditMessagePayload;
import static org.folio.services.util.MessagingModulesUtil.filter;

@Component
public class KafkaPublisherServiceImpl implements PublisherService {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublisherServiceImpl.class);

  private static final boolean EVENT_PAYLOAD_AUDIT_ENABLED =
    Boolean.parseBoolean(MODULE_SPECIFIC_ARGS.getOrDefault("event.payload.audit.enabled", "false"));

  private Cache cache;
  private AuditService auditService;
  private PublishingService publishingService;

  public KafkaPublisherServiceImpl(@Autowired Vertx vertx,
                                   @Autowired Cache cache) {
    this.cache = cache;
    this.auditService = AuditService.createProxy(vertx);
    this.publishingService = PublishingService.createProxy(vertx);
  }

  @Override
  public Future<Boolean> publishEvent(Event event, String tenantId) {
    Promise<Boolean> promise = Promise.promise();
    if (EVENT_PAYLOAD_AUDIT_ENABLED) {
      saveAuditMessagePayload(event, tenantId);
    }
    auditService.saveAuditMessage(constructJsonAuditMessage(event, tenantId, AuditMessage.State.CREATED));
    verifyPublisher(event, tenantId)
      .setHandler(ar -> {
        if (ar.succeeded()) {
          publishingService.sendEvent(JsonObject.mapFrom(event), tenantId);
          promise.complete(true);
        } else {
          promise.fail(ar.cause());
        }
      });
    return promise.future();
  }

  /**
   * Checks if publisher of the event is registered and event type is activated for the tenant
   *
   * @param event    event to publish
   * @param tenantId tenant id
   * @return future with true if succeeded
   */
  private Future<Boolean> verifyPublisher(Event event, String tenantId) {
    return cache.getMessagingModules()
      .map(messagingModules -> new ArrayList<>(filter(messagingModules,
        new MessagingModuleFilter()
          .withModuleId(event.getEventMetadata().getPublishedBy())
          .withTenantId(tenantId)
          .withModuleRole(PUBLISHER)
          .withEventType(event.getEventType()))))
      .compose(publishers -> {
        if (isEmpty(publishers)) {
          String errorMessage = format("%s is not registered as PUBLISHER for event type %s", event.getEventMetadata().getPublishedBy(), event.getEventType());
          LOGGER.error(errorMessage);
          auditService.saveAuditMessage(constructJsonAuditMessage(event, tenantId, AuditMessage.State.REJECTED, errorMessage));
          return Future.failedFuture(new BadRequestException(errorMessage));
        } else if (Boolean.FALSE.equals(publishers.get(0).getActivated())) {
          String error = format("Event type %s is not activated for tenant %s", event.getEventType(), tenantId);
          LOGGER.error(error);
          auditService.saveAuditMessage(constructJsonAuditMessage(event, tenantId, AuditMessage.State.REJECTED, error));
          return Future.failedFuture(new BadRequestException(error));
        }
        return Future.succeededFuture(true);
      });
  }

  private void saveAuditMessagePayload(Event event, String tenantId) {
    if (isNotEmpty(event.getEventPayload())) {
      auditService.saveAuditMessagePayload(constructJsonAuditMessagePayload(event), tenantId);
    }
  }

}
