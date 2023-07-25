package org.folio.kafka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;

public class PubSubConfigTest {
  private static final String ENV = "env";
  private static final String EVENT_TYPE = "eventType";
  private static final String TENANT = "tenant";
  private Supplier<PubSubConfig> pubSubConfigSupplier = () -> new PubSubConfig(ENV, TENANT, EVENT_TYPE);

  @Test
  public void checkTopicNameWhenTenantCollectionEnabled() {
    PubSubConfig pubSubConfig = pubSubConfigSupplier.get();
    assertTrue(pubSubConfig.getTopicName().contains("env.pub-sub.tenant.eventType"));
    assertTrue(pubSubConfig.getTopicName().contains(ENV + ".pub-sub." + TENANT + "." + EVENT_TYPE));
    assertTrue(pubSubConfig.getGroupId().contains(ENV + ".pub-sub." + TENANT + "." + EVENT_TYPE + ".mod-pubsub"));
    assertEquals(TENANT, pubSubConfig.getTenant());
    assertEquals(EVENT_TYPE, pubSubConfig.getEventType());

    String qualifier = "ALL";
    PubSubConfig.setTenantCollectionTopicsQualifier(qualifier);
    pubSubConfig = pubSubConfigSupplier.get();
    assertTrue(pubSubConfig.getTopicName().contains(ENV + ".pub-sub." + qualifier + "." + EVENT_TYPE));
    assertTrue(pubSubConfig.getGroupId().contains(ENV + ".pub-sub." + qualifier + "." + EVENT_TYPE + ".mod-pubsub"));
    assertEquals(TENANT, pubSubConfig.getTenant());
    assertEquals(EVENT_TYPE, pubSubConfig.getEventType());
  }

  @Test(expected = IllegalArgumentException.class)
  public void checkQualifierMatchesRegex() {
    PubSubConfig.setTenantCollectionTopicsQualifier("bad_value");
  }

  @After
  public void after() {
    PubSubConfig.setTenantCollectionTopicsQualifier(null);
  }
}
