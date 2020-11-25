package org.folio.kafka;

import org.apache.commons.lang3.StringUtils;

import static java.lang.String.join;

public class KafkaTopicNameHelper {
  private static final String DEFAULT_NAMESPACE = "Default";

  private KafkaTopicNameHelper() {
    super();
  }

  //TODO: add unit test
  public static String formatTopicName(String env, String nameSpace, String tenant, String eventType) {
    return join(".", env, nameSpace, tenant, eventType);
  }

  //TODO: add unit test
  public static String getEventTypeFromTopicName(String topic) {
    int eventTypeIndex = StringUtils.isBlank(topic) ? -1 : topic.lastIndexOf('.') + 1;
    if (eventTypeIndex > 0) {
      return topic.substring(eventTypeIndex);
    } else {
      throw new RuntimeException("Invalid topic name: " + topic + " - expected env.nameSpace.tenant.eventType");
    }
  }

  //TODO: add unit test
  public static String formatGroupName(String eventType, String moduleName) {
    return join(".", eventType, moduleName);
  }

  //TODO: add unit test
  public static String formatSubscriptionPattern(String env, String nameSpace, String eventType) {
    return join("\\.", env, nameSpace, "\\w{4,}", eventType);
  }

  public static String getDefaultNameSpace() {
    return DEFAULT_NAMESPACE;
  }

  //TODO: add unit test
  public static SubscriptionDefinition createSubscriptionDefinition(String env, String nameSpace, String eventType) {
    return SubscriptionDefinition.builder()
      .eventType(eventType)
      .subscriptionPattern(formatSubscriptionPattern(env, nameSpace, eventType))
      .build();
  }
}
