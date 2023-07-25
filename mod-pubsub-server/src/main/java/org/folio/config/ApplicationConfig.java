package org.folio.config;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.folio.kafka.KafkaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import io.vertx.kafka.admin.KafkaAdminClient;

@Configuration
@ComponentScan(basePackages = {
  "org.folio.dao",
  "org.folio.services",
  "org.folio.rest",
  "org.folio.kafka",
  "org.folio.config.user"})
public class ApplicationConfig {

  private KafkaAdminClient adminClient;
  @Bean
  public KafkaAdminClient kafkaAdminClient(@Autowired Vertx vertx, @Autowired KafkaConfig config) {
    Map<String, String> configs = new HashMap<>();
    configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaUrl());
    adminClient = KafkaAdminClient.create(vertx, configs);
    return adminClient;
  }

  @PreDestroy
  public void closeAdminClient() {
    adminClient.close(1_000);
  }

}
