package org.folio.services.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.folio.kafka.PubSubConsumerConfig;
import org.folio.services.KafkaTopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class KafkaTopicServiceImpl implements KafkaTopicService {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTopicServiceImpl.class);

  @Autowired
  private AdminClient kafkaAdminClient;

  @Override
  public Future<Boolean> createTopics(List<String> eventTypes, String tenantId, int numPartitions, short replicationFactor) {
    List<NewTopic> topics = eventTypes.stream()
      .map(eventType -> new NewTopic(new PubSubConsumerConfig(tenantId, eventType).getTopicName(), numPartitions, replicationFactor))
      .collect(Collectors.toList());
    List<Future> results = new ArrayList<>();
    kafkaAdminClient.createTopics(topics).values()
      .forEach((String topic, KafkaFuture<Void> future) -> {
        try {
          future.get();
          results.add(Future.succeededFuture());
          LOGGER.info("Created topic {}", topic);
        } catch (Exception e) {
          results.add(Future.failedFuture(e));
          LOGGER.error("Error creating topic {}. Cause: {}", topic, e.getMessage());
        }
      });
    Future<Boolean> result = Future.future();
    CompositeFuture.join(results)
      .setHandler(ar -> result.complete(ar.succeeded()));
    return result;
  }
}
