package org.folio.services;

import io.vertx.core.Future;

import java.util.List;

/**
 * Service for creating Kafka topics
 */
public interface KafkaTopicService {

  /**
   * Creates Kafka topics for specified event types
   *
   * @param eventTypes        list of event types, for which topics should be created
   * @param tenantId          tenant id, for which topics should be created
   * @param numPartitions     number of partitions
   * @param replicationFactor replication factor
   * @return future with true if succeeded
   */
  Future<Boolean> createTopics(List<String> eventTypes, String tenantId, int numPartitions, short replicationFactor);
}
