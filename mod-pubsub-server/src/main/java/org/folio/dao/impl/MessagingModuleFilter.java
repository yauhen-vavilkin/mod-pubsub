package org.folio.dao.impl;

import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;

/**
 * Filter for messagingModule entity
 */
public class MessagingModuleFilter {

  private String eventType;
  private String moduleId;
  private String tenantId;
  private String moduleRole;
  private String applied;
  private String subscriberCallback;

  public void byEventType(String eventType) {
    this.eventType = eventType;
  }

  public void byModuleId(String moduleId) {
    this.moduleId = moduleId;
  }

  public void byTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public void byModuleRole(ModuleRole moduleRole) {
    this.moduleRole = moduleRole.value();
  }

  public void byApplied(boolean applied) {
    this.applied = String.valueOf(applied);
  }

  public void bySubscriberCallback(String subscriberCallback) {
    this.subscriberCallback = subscriberCallback;
  }

  public String getEventType() {
    return eventType;
  }

  public String getModuleId() {
    return moduleId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getModuleRole() {
    return moduleRole;
  }

  public String getApplied() {
    return applied;
  }

  public String getSubscriberCallback() {
    return subscriberCallback;
  }
}
