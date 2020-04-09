package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.admin.NewTopic;
import org.apache.commons.lang3.StringUtils;
import org.folio.kafka.PubSubConfig;
import org.folio.services.KafkaTopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KafkaTopicServiceImpl implements KafkaTopicService {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTopicServiceImpl.class);

  private KafkaAdminClient kafkaAdminClient;

  public KafkaTopicServiceImpl(@Autowired KafkaAdminClient kafkaAdminClient) {
    this.kafkaAdminClient = kafkaAdminClient;
  }

  @Override
  public Future<Boolean> createTopics(List<String> eventTypes, String tenantId, int numPartitions, short replicationFactor) {
    Promise<Boolean> promise = Promise.promise();
    List<NewTopic> topics = eventTypes.stream()
      .map(eventType -> new NewTopic(new PubSubConfig(tenantId, eventType).getTopicName(), numPartitions, replicationFactor))
      .collect(Collectors.toList());
    kafkaAdminClient.createTopics(topics, ar -> {
      if (ar.succeeded()) {
        LOGGER.info("Created topics: [{}]", StringUtils.join(eventTypes, ","));
        promise.complete(true);
      } else {
        LOGGER.error("Some of the topics [{}] were not created. Cause: {}", StringUtils.join(eventTypes, ","), ar.cause().getMessage());
        promise.complete(false);
      }
    });
    return promise.future();
  }
}
