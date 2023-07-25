package org.folio.kafka;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.tools.utils.ModuleName;

import static java.lang.String.join;

public class PubSubConfig {
  private static final String PUB_SUB_PREFIX = "pub-sub";
  private static final String TENANT_COLLECTION_TOPICS_ENV_VAR_NAME = "KAFKA_PRODUCER_TENANT_COLLECTION";
  private static final String TENANT_COLLECTION_MATCH_REGEX = "[A-Z][A-Z0-9]{0,30}";
  private static String tenantCollectionTopicQualifier;
  private static boolean isTenantCollectionTopicsEnabled;
  private String tenant;
  private String eventType;
  private String groupId;
  private String topicName;

  static {
    setTenantCollectionTopicsQualifier();
  }

  public static void setTenantCollectionTopicsQualifier() {
    try {
      setTenantCollectionTopicsQualifier(System.getenv(TENANT_COLLECTION_TOPICS_ENV_VAR_NAME));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
        String.format("%s environment variable: %s", TENANT_COLLECTION_TOPICS_ENV_VAR_NAME, e.getMessage()), e);
    }
  }

  public PubSubConfig(String env, String tenant, String eventType) {
    this.tenant = tenant;
    this.eventType = eventType;
    /* moduleNameWithVersion variable need for unique topic and group names for different pub-sub versions.
    It was encapsulated here, in constructor, for better creating/subscribing/sending events.*/
    String moduleNameWithVersion = ModuleName.getModuleName().replace("_", "-") + "-" + ModuleName.getModuleVersion();
    String topicQualifier = isTenantCollectionTopicsEnabled ? tenantCollectionTopicQualifier : tenant;
    this.groupId = join(".", env, PUB_SUB_PREFIX, topicQualifier, eventType, moduleNameWithVersion);
    this.topicName = join(".", env, PUB_SUB_PREFIX, topicQualifier, eventType, moduleNameWithVersion);
  }

  public static void setTenantCollectionTopicsQualifier(String value) {
    tenantCollectionTopicQualifier = value;
    isTenantCollectionTopicsEnabled = StringUtils.isNotEmpty(tenantCollectionTopicQualifier);

    if (isTenantCollectionTopicsEnabled &&
      !tenantCollectionTopicQualifier.matches(TENANT_COLLECTION_MATCH_REGEX)) {
      throw new IllegalArgumentException(
        String.format("Tenant collection qualifier doesn't match %s",
          TENANT_COLLECTION_MATCH_REGEX));
    }
  }

  public String getTenant() {
    return tenant;
  }

  public String getEventType() {
    return eventType;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getTopicName() {
    return topicName;
  }
}
