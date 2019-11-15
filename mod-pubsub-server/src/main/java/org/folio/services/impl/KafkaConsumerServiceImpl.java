package org.folio.services.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.folio.HttpStatus;
import org.folio.dao.MessagingModuleDao;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.PubSubConfig;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.AuditMessageService;
import org.folio.services.ConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

@Component
public class KafkaConsumerServiceImpl implements ConsumerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerServiceImpl.class);

  private static final int TIMEOUT = 2000;

  private Vertx vertx;
  private KafkaConfig kafkaConfig;
  private MessagingModuleDao messagingModuleDao;
  private AuditMessageService auditMessageService;

  public KafkaConsumerServiceImpl(@Autowired Vertx vertx,
                                  @Autowired KafkaConfig kafkaConfig,
                                  @Autowired MessagingModuleDao messagingModuleDao,
                                  @Autowired AuditMessageService auditMessageService) {
    this.vertx = vertx;
    this.kafkaConfig = kafkaConfig;
    this.messagingModuleDao = messagingModuleDao;
    this.auditMessageService = auditMessageService;
  }

  @Override
  public Future<Boolean> subscribe(String moduleId, List<String> eventTypes, OkapiConnectionParams params) {
    Future<Boolean> future = Future.future();
    Set<String> topics = eventTypes.stream()
      .map(eventType -> new PubSubConfig(params.getTenantId(), eventType).getTopicName())
      .collect(Collectors.toSet());
    Map<String, String> consumerProps = kafkaConfig.getConsumerProps();
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, params.getTenantId() + "." + moduleId);
    KafkaConsumer.<String, String>create(vertx, consumerProps)
      .subscribe(topics, ar -> {
        if (ar.succeeded()) {
          LOGGER.info("Subscribed to topics [{}]", StringUtils.join(topics, ","));
          future.complete(true);
        } else {
          LOGGER.error("Could not subscribe to some of the topics [{}]", ar.cause(), StringUtils.join(topics, ","));
          future.fail(ar.cause());
        }
      }).handler(getEventReceivedHandler(params));
    return future;
  }

  private Handler<KafkaConsumerRecord<String, String>> getEventReceivedHandler(OkapiConnectionParams params) {
    return record -> {
      try {
        String value = record.value();
        LOGGER.info("Received event {}", value);
        Event event = new JsonObject(value).mapTo(Event.class);
        saveAuditMessage(event, params.getTenantId(), AuditMessage.State.RECEIVED);
        deliverEvent(event, params);
      } catch (Exception e) {
        LOGGER.error("Error reading event value", e);
      }
    };
  }

  protected Future<Void> deliverEvent(Event event, OkapiConnectionParams params) {
    return messagingModuleDao.get(new MessagingModuleFilter()
      .withTenantId(params.getTenantId())
      .withModuleRole(SUBSCRIBER)
      .withEventType(event.getEventType()))
      .compose(messagingModuleList -> {
        if (CollectionUtils.isEmpty(messagingModuleList)) {
          String errorMessage = format("There is no SUBSCRIBERS registered for event type %s. Event %s will not be delivered", event.getEventType(), event.getId());
          LOGGER.error(errorMessage);
        } else {
          messagingModuleList.parallelStream()
            .forEach(subscriber -> doRequest(event, subscriber.getSubscriberCallback(), params)
              .setHandler(getEventDeliveredHandler(event, params.getTenantId(), subscriber)));
        }
        return Future.succeededFuture();
      });
  }

  private Handler<AsyncResult<HttpClientResponse>> getEventDeliveredHandler(Event event, String tenantId, MessagingModule subscriber) {
    return ar -> {
      if (ar.failed()) {
        LOGGER.error("Event {} was not delivered to {}", ar.cause(), event.getId(), subscriber.getSubscriberCallback());
        saveAuditMessage(event, tenantId, AuditMessage.State.REJECTED);
      } else if (ar.result().statusCode() != HttpStatus.HTTP_OK.toInt()
        && ar.result().statusCode() != HttpStatus.HTTP_CREATED.toInt()
        && ar.result().statusCode() != HttpStatus.HTTP_NO_CONTENT.toInt()) {
        LOGGER.error("Error delivering event {} to {}, response status code is {}, {}",
          event.getId(), subscriber.getSubscriberCallback(), ar.result().statusCode(), ar.result().statusMessage());
        saveAuditMessage(event, tenantId, AuditMessage.State.REJECTED);
      } else {
        LOGGER.info("Delivered event {} to {}", event.getId(), subscriber.getSubscriberCallback());
        saveAuditMessage(event, tenantId, AuditMessage.State.DELIVERED);
      }
    };
  }

  protected Future<HttpClientResponse> doRequest(Event event, String callbackPath, OkapiConnectionParams params) {
    Future<HttpClientResponse> future = Future.future();
    try {
      HttpClientRequest request = getHttpClient().requestAbs(HttpMethod.POST, params.getOkapiUrl() + callbackPath);

      CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
      headers.addAll(params.getHeaders());
      headers.add("Content-type", "application/json").add("Accept", "application/json, text/plain");
      headers.entries().forEach(header -> request.putHeader(header.getKey(), header.getValue()));

      request.exceptionHandler(future::fail);
      request.handler(future::complete);

      if (event.getEventPayload() == null) {
        request.end();
      } else {
        request.end(event.getEventPayload());
      }
    } catch (Exception e) {
      LOGGER.error("Failed to deliver event {} to {}", e, event.getId(), callbackPath);
      future.fail(e);
    }
    return future;
  }

  private HttpClient getHttpClient() {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(TIMEOUT);
    options.setIdleTimeout(TIMEOUT);
    return vertx.createHttpClient(options);
  }

  private void saveAuditMessage(Event event, String tenantId, AuditMessage.State state) {
    auditMessageService.saveAuditMessage(new AuditMessage()
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
