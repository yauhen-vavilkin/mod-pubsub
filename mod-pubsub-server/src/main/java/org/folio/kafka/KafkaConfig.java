package org.folio.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.folio.rest.util.SimpleConfigurationReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class KafkaConfig {

  @Value("${KAFKA_HOST}")
  private String kafkaHost;
  @Value("${KAFKA_PORT}")
  private String kafkaPort;
  @Value("${OKAPI_URL}")
  private String okapiUrl;
  @Value("${REPLICATION_FACTOR:1}")
  private int replicationFactor;
  @Value("${NUMBER_OF_PARTITIONS:1}")
  private int numberOfPartitions;
  @Value("${ENV:folio}")
  private String envId;
  @Value("${security.protocol:}")
  private String kafkaSecurityProtocolConfig;
  @Value("${ssl.protocol:}")
  private String kafkaSslProtocolConfig;
  @Value("${ssl.key.password:}")
  private String kafkaSslKeyPasswordConfig;
  @Value("${ssl.truststore.location:}")
  private String kafkaSslTruststoreLocationConfig;
  @Value("${ssl.truststore.password:}")
  private String kafkaSslTruststorePasswordConfig;
  @Value("${ssl.truststore.type:}")
  private String kafkaSslTruststoreTypeConfig;
  @Value("${ssl.keystore.location:}")
  private String kafkaSslKeystoreLocationConfig;
  @Value("${ssl.keystore.password:}")
  private String kafkaSslKeystorePasswordConfig;
  @Value("${ssl.keystore.type:}")
  private String kafkaSslKeystoreTypeConfig;

  public static final String KAFKA_SECURITY_PROTOCOL_DEFAULT = "PLAINTEXT";
  public static final String KAFKA_SSL_PROTOCOL_DEFAULT = "TLSv1.2";
  public static final String KAFKA_SSL_TRUSTSTORE_TYPE_DEFAULT = "JKS";
  public static final String KAFKA_SSL_KEYSTORE_TYPE_DEFAULT = "JKS";

  public String getKafkaHost() {
    return kafkaHost;
  }

  public String getKafkaPort() {
    return kafkaPort;
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public Map<String, String> getProducerProps() {
    Map<String, String> producerProps = new HashMap<>();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaUrl());
    producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    ensureSecurityProps(producerProps);
    return producerProps;
  }

  public Map<String, String> getConsumerProps() {
    Map<String, String> consumerProps = new HashMap<>();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaUrl());
    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    consumerProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    ensureSecurityProps(consumerProps);
    return consumerProps;
  }

  public String getKafkaUrl() {
    return kafkaHost + ":" + kafkaPort;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public int getNumberOfPartitions() {
    return numberOfPartitions;
  }

  public String getEnvId() {
    return envId;
  }

  private void ensureSecurityProps(Map<String, String> clientProps) {
    clientProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSecurityProtocolConfig, SpringKafkaProperties.KAFKA_SECURITY_PROTOCOL, KAFKA_SECURITY_PROTOCOL_DEFAULT));
    clientProps.put(SslConfigs.SSL_PROTOCOL_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslProtocolConfig, SpringKafkaProperties.KAFKA_SSL_PROTOCOL, KAFKA_SSL_PROTOCOL_DEFAULT));
    clientProps.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslKeyPasswordConfig, SpringKafkaProperties.KAFKA_SSL_KEY_PASSWORD, null));
    clientProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslTruststoreLocationConfig, SpringKafkaProperties.KAFKA_SSL_TRUSTSTORE_LOCATION, null));
    clientProps.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslTruststorePasswordConfig, SpringKafkaProperties.KAFKA_SSL_TRUSTSTORE_PASSWORD, null));
    clientProps.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslTruststoreTypeConfig, SpringKafkaProperties.KAFKA_SSL_TRUSTSTORE_TYPE, KAFKA_SSL_TRUSTSTORE_TYPE_DEFAULT));
    clientProps.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslKeystoreLocationConfig, SpringKafkaProperties.KAFKA_SSL_KEYSTORE_LOCATION, null));
    clientProps.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslKeystorePasswordConfig, SpringKafkaProperties.KAFKA_SSL_KEYSTORE_PASSWORD, null));
    clientProps.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, SimpleConfigurationReader.getValue(
      kafkaSslKeystoreTypeConfig, SpringKafkaProperties.KAFKA_SSL_KEYSTORE_TYPE, KAFKA_SSL_KEYSTORE_TYPE_DEFAULT));
  }
}
