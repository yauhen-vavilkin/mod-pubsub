package org.folio.kafka;

import io.kcache.KafkaCacheConfig;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Getter
@Builder
@ToString
public class KafkaConfig {
  public static final String KAFKA_CONSUMER_AUTO_OFFSET_RESET_CONFIG = "kafka.consumer.auto.offset.reset";
  public static final String KAFKA_CONSUMER_AUTO_OFFSET_RESET_CONFIG_DEFAULT = "earliest";

  public static final String KAFKA_CONSUMER_METADATA_MAX_AGE_CONFIG = "kafka.consumer.metadata.max.age.ms";
  public static final String KAFKA_CONSUMER_METADATA_MAX_AGE_CONFIG_DEFAULT = "30000";

  public static final String KAFKA_NUMBER_OF_PARTITIONS = "kafka.number_of_partitions";
  public static final String KAFKA_NUMBER_OF_PARTITIONS_DEFAULT = "10";

  public static final String KAFKA_CONSUMER_MAX_POLL_RECORDS_CONFIG = "kafka.consumer.max.poll.records";
  public static final String KAFKA_CONSUMER_MAX_POLL_RECORDS_CONFIG_DEFAULT = "100";

  public static final String KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS_CONFIG = "kafka.consumer.max.poll.interval.ms";
  public static final String KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS_CONFIG_DEFAULT = "300000";

  private static final String KAFKA_CACHE_TOPIC_PROPERTY = "kafkacache.topic";

  private final String kafkaHost;
  private final String kafkaPort;
  private final String okapiUrl;
  private final int replicationFactor;
  private final String envId;

  public Map<String, String> getProducerProps() {
    Map<String, String> producerProps = new HashMap<>();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaUrl());
    producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    return producerProps;
  }

  public Map<String, String> getConsumerProps() {
    Map<String, String> consumerProps = new HashMap<>();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaUrl());
    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, SimpleConfigurationReader.getValue(KAFKA_CONSUMER_MAX_POLL_RECORDS_CONFIG, KAFKA_CONSUMER_MAX_POLL_RECORDS_CONFIG_DEFAULT));
    consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, SimpleConfigurationReader.getValue(KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS_CONFIG, KAFKA_CONSUMER_MAX_POLL_INTERVAL_MS_CONFIG_DEFAULT));
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, SimpleConfigurationReader.getValue(KAFKA_CONSUMER_AUTO_OFFSET_RESET_CONFIG, KAFKA_CONSUMER_AUTO_OFFSET_RESET_CONFIG_DEFAULT));
    consumerProps.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, SimpleConfigurationReader.getValue(KAFKA_CONSUMER_METADATA_MAX_AGE_CONFIG, KAFKA_CONSUMER_METADATA_MAX_AGE_CONFIG_DEFAULT));
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    return consumerProps;
  }

  public KafkaCacheConfig getCacheConfig() {
    Properties props = new Properties();
    props.put(KafkaCacheConfig.KAFKACACHE_BOOTSTRAP_SERVERS_CONFIG, /*"PLAINTEXT://" +*/ getKafkaUrl());
    props.put(KAFKA_CACHE_TOPIC_PROPERTY, "events_cache");
    return new KafkaCacheConfig(props);
  }

  public String getKafkaUrl() {
    return kafkaHost + ":" + kafkaPort;
  }

  public int getNumberOfPartitions() {
    return Integer.parseInt(SimpleConfigurationReader.getValue(KAFKA_NUMBER_OF_PARTITIONS, KAFKA_NUMBER_OF_PARTITIONS_DEFAULT));
  }

}
