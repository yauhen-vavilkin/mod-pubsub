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
   * @param eventTypes list of event types, for which topics should be created
   * @param tenantId   tenant id, for which topics should be created
   * @return succeeded future if created topics, failed future otherwise
   */
  Future<Void> createTopics(List<String> eventTypes, String tenantId);
}
