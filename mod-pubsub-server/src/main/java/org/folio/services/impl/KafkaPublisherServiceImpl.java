package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;
import org.apache.commons.lang3.StringUtils;
import org.folio.kafka.PubSubConfig;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.AuditMessagePayload;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.services.MessagingModuleService;
import org.folio.services.PublisherService;
import org.folio.services.audit.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.Date;
import java.util.UUID;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

@Component
public class KafkaPublisherServiceImpl implements PublisherService {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublisherServiceImpl.class);

  private KafkaProducer<String, String> producer;
  private MessagingModuleService messagingModuleService;
  private AuditService auditService;

  public KafkaPublisherServiceImpl(@Autowired Vertx vertx,
                                   @Autowired KafkaProducer<String, String> producer,
                                   @Autowired MessagingModuleService messagingModuleService) {
    this.producer = producer;
    this.messagingModuleService = messagingModuleService;
    this.auditService = AuditService.createProxy(vertx);
  }

  @Override
  public Future<Boolean> publishEvent(Event event, String tenantId) {
    saveAuditMessagePayload(event, tenantId);
    saveAuditMessage(event, tenantId, AuditMessage.State.CREATED);
    return verifyPublisher(event, tenantId)
      .compose(ar -> checkForRegisteredSubscribers(event, tenantId))
      .compose(ar -> sendEvent(event, tenantId));
  }

  /**
   * Checks if publisher of the event is registered and event type is activated for the tenant
   *
   * @param event    event to publish
   * @param tenantId tenant id
   * @return future with true if succeeded
   */
  private Future<Boolean> verifyPublisher(Event event, String tenantId) {
    return messagingModuleService.get(new MessagingModuleFilter()
      .withModuleId(event.getEventMetadata().getPublishedBy())
      .withTenantId(tenantId)
      .withModuleRole(PUBLISHER)
      .withEventType(event.getEventType()))
      .compose(messagingModuleCollection -> {
        if (messagingModuleCollection.getTotalRecords() == 0) {
          String errorMessage = format("%s is not registered as PUBLISHER for event type %s", event.getEventMetadata().getPublishedBy(), event.getEventType());
          LOGGER.error(errorMessage);
          saveAuditMessage(event, tenantId, AuditMessage.State.REJECTED);
          return Future.failedFuture(new BadRequestException(errorMessage));
        } else if (Boolean.FALSE.equals(messagingModuleCollection.getMessagingModules().get(0).getActivated())) {
          String error = format("Event type %s is not activated for tenant %s", event.getEventType(), tenantId);
          LOGGER.error(error);
          saveAuditMessage(event, tenantId, AuditMessage.State.REJECTED);
          return Future.failedFuture(new BadRequestException(error));
        }
        return Future.succeededFuture(true);
      });
  }

  /**
   * Checks for registered subscribers for event type, if no subscribers found event is rejected.
   *
   * @param event    event to publish
   * @param tenantId tenant id
   * @return future with true if succeeded
   */
  private Future<Boolean> checkForRegisteredSubscribers(Event event, String tenantId) {
    return messagingModuleService.get(new MessagingModuleFilter()
      .withTenantId(tenantId)
      .withModuleRole(SUBSCRIBER)
      .withEventType(event.getEventType()))
      .compose(messagingModuleCollection -> {
        if (messagingModuleCollection.getTotalRecords() == 0) {
          String errorMessage = format("There is no SUBSCRIBERS registered for event type %s. Event %s will not be published", event.getEventType(), event.getId());
          LOGGER.error(errorMessage);
          saveAuditMessage(event, tenantId, AuditMessage.State.REJECTED);
          return Future.failedFuture(new BadRequestException(errorMessage));
        } else {
          return Future.succeededFuture(true);
        }
      });
  }

  /**
   * Publishes event to the appropriate topic
   *
   * @param event    event to publish
   * @param tenantId tenant id
   * @return future with true if succeeded
   */
  private Future<Boolean> sendEvent(Event event, String tenantId) {
    Future<Boolean> future = Future.future();
    PubSubConfig config = new PubSubConfig(tenantId, event.getEventType());
    producer.write(new KafkaProducerRecordImpl<>(config.getTopicName(), JsonObject.mapFrom(event).encode()), done -> {
      if (done.succeeded()) {
        LOGGER.info("Sent event to topic {}", config.getTopicName());
        saveAuditMessage(event, tenantId, AuditMessage.State.PUBLISHED);
        future.complete(true);
      } else {
        LOGGER.error("Event was not sent", done.cause());
        saveAuditMessage(event, tenantId, AuditMessage.State.REJECTED);
        future.fail(done.cause());
      }
    });
    return future;
  }

  private void saveAuditMessagePayload(Event event, String tenantId) {
    if (StringUtils.isNotEmpty(event.getEventPayload())) {
      auditService
        .saveAuditMessagePayload(JsonObject.mapFrom(new AuditMessagePayload()
          .withEventId(event.getId())
          .withContent(event.getEventPayload())), tenantId);
    }
  }

  private void saveAuditMessage(Event event, String tenantId, AuditMessage.State state) {
    auditService.saveAuditMessage(JsonObject.mapFrom(new AuditMessage()
      .withId(UUID.randomUUID().toString())
      .withEventId(event.getId())
      .withEventType(event.getEventType())
      .withTenantId(tenantId)
      .withCorrelationId(event.getEventMetadata().getCorrelationId())
      .withCreatedBy(event.getEventMetadata().getCreatedBy())
      .withPublishedBy(event.getEventMetadata().getPublishedBy())
      .withAuditDate(new Date())
      .withState(state)));
  }

}
