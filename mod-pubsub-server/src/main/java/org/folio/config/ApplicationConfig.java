package org.folio.config;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.folio.kafka.KafkaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ComponentScan(basePackages = {
  "org.folio.dao",
  "org.folio.services",
  "org.folio.rest",
  "org.folio.kafka"})
public class ApplicationConfig {

  @Bean
  public KafkaProducer kafkaProducer(@Autowired Vertx vertx, @Autowired KafkaConfig config) {
    return KafkaProducer.createShared(vertx, "pub-sub-producer", config.getProducerProps());
  }

  @Bean
  public KafkaAdminClient kafkaAdminClient(@Autowired Vertx vertx, @Autowired KafkaConfig config) {
    Map<String, String> configs = new HashMap<>();
    configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaUrl());
    return KafkaAdminClient.create(vertx, configs);
  }

}
