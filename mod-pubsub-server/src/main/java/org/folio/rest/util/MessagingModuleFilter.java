package org.folio.rest.util;

import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;

/**
 * Filter for messagingModule entity
 */
public class MessagingModuleFilter {

  private String eventType;
  private String moduleId;
  private String tenantId;
  private ModuleRole moduleRole;
  private Boolean activated;
  private String subscriberCallback;

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getModuleId() {
    return moduleId;
  }

  public void setModuleId(String moduleId) {
    this.moduleId = moduleId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public ModuleRole getModuleRole() {
    return moduleRole;
  }

  public void setModuleRole(ModuleRole moduleRole) {
    this.moduleRole = moduleRole;
  }

  public Boolean getActivated() {
    return activated;
  }

  public void setActivated(Boolean activated) {
    this.activated = activated;
  }

  public String getSubscriberCallback() {
    return subscriberCallback;
  }

  public void setSubscriberCallback(String subscriberCallback) {
    this.subscriberCallback = subscriberCallback;
  }

  public MessagingModuleFilter withEventType(String eventType) {
    this.eventType = eventType;
    return this;
  }

  public MessagingModuleFilter withModuleId(String moduleId) {
    this.moduleId = moduleId;
    return this;
  }

  public MessagingModuleFilter withTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public MessagingModuleFilter withModuleRole(ModuleRole moduleRole) {
    this.moduleRole = moduleRole;
    return this;
  }

  public MessagingModuleFilter withActivated(Boolean activated) {
    this.activated = activated;
    return this;
  }

  public MessagingModuleFilter withSubscriberCallback(String subscriberCallback) {
    this.subscriberCallback = subscriberCallback;
    return this;
  }

}
