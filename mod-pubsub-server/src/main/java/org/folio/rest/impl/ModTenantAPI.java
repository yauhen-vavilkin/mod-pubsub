package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.folio.dao.util.LiquibaseUtil;
import org.folio.kafka.KafkaConfig;
import org.folio.kafka.PubSubConsumerConfig;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModTenantAPI extends TenantAPI {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModTenantAPI.class);
  // stub event type needed to create topic and consumer for initial testing of kafka config,
  // will be removed in scope of {@link https://issues.folio.org/browse/MODPUBSUB-42}
  private static final String STUB_EVENT_TYPE = "record_created";

  @Autowired
  private KafkaConfig kafkaConfig;
  @Autowired
  private AdminClient kafkaAdminClient;

  public ModTenantAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Validate
  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers, Handler<AsyncResult<Response>> handler, Context context) {
    super.postTenant(tenantAttributes, headers, postTenantAr -> {
      if (postTenantAr.failed()) {
        handler.handle(postTenantAr);
      } else {
        String tenantId = headers.get(RestVerticle.OKAPI_HEADER_TENANT);
        Vertx vertx = context.owner();
        vertx.executeBlocking(
          blockingFuture -> {
            LiquibaseUtil.initializeSchemaForTenant(vertx, tenantId);
            // Create stub topic and stub consumer
            createTopics(tenantId);
            createKafkaConsumer(tenantId, vertx);
            blockingFuture.complete();
          },
          result -> handler.handle(postTenantAr)
        );
      }
    }, context);
  }

  private void createTopics(String tenantId) {
    int numPartitions = 1;
    short replicationFactor = 1;
    List<String> eventTypes = new ArrayList<>();
    eventTypes.add(STUB_EVENT_TYPE);
    List<NewTopic> topics = eventTypes.stream()
      .map(eventType -> new NewTopic(new PubSubConsumerConfig(tenantId, eventType).getTopicName(), numPartitions, replicationFactor))
      .collect(Collectors.toList());
    kafkaAdminClient.createTopics(topics);
  }

  private KafkaConsumer<String, String> createKafkaConsumer(String tenantId, Vertx vertx) {
    PubSubConsumerConfig pubSubConfig = new PubSubConsumerConfig(tenantId, STUB_EVENT_TYPE);
    Map<String, String> consumerProps = kafkaConfig.getConsumerProps();
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, pubSubConfig.getGroupId());
    return KafkaConsumer.<String, String>create(vertx, consumerProps)
      .subscribe(pubSubConfig.getTopicName(), ar -> {
        if (ar.succeeded()) {
          LOGGER.info("Subscribed to topic {}", pubSubConfig.getTopicName());
        } else {
          LOGGER.error("Could not subscribe to topic", ar.cause());
        }
      })
      .handler(record -> {
        try {
          String event = record.value();
          LOGGER.info("Received event {}", event);
        } catch (Exception e) {
          LOGGER.error("Error reading event value", e);
        }
      });
  }

}
