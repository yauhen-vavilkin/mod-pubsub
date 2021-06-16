package org.folio.kafka;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;


public class KafkaConfigTest {

  @Test
  public void shouldReturnProducerProperties() {
    Map<String, String> producerProps = new KafkaConfig().getProducerProps();

    Assert.assertEquals("PLAINTEXT", producerProps.get("security.protocol"));
    Assert.assertEquals("TLSv1.2", producerProps.get("ssl.protocol"));
    Assert.assertEquals("JKS", producerProps.get("ssl.truststore.type"));
    Assert.assertEquals("JKS", producerProps.get("ssl.keystore.type"));
    Assert.assertNull(producerProps.get("ssl.keystore.password"));
  }

  @Test
  public void shouldReturnConsumerProperties() {
    Map<String, String> consumerProps = new KafkaConfig().getConsumerProps();

    Assert.assertEquals("PLAINTEXT", consumerProps.get("security.protocol"));
    Assert.assertEquals("TLSv1.2", consumerProps.get("ssl.protocol"));
    Assert.assertEquals("JKS", consumerProps.get("ssl.truststore.type"));
    Assert.assertEquals("JKS", consumerProps.get("ssl.keystore.type"));
    Assert.assertNull(consumerProps.get("ssl.keystore.password"));
  }
}
