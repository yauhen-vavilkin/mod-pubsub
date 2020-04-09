package org.folio.services.util;

import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.MessagingModuleFilter;

import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public final class MessagingModulesUtil {

  private MessagingModulesUtil() {
  }

  public static Set<MessagingModule> filter(Set<MessagingModule> modules, MessagingModuleFilter filter) {
    if (isNotEmpty(modules) && filter != null) {
      return modules.stream()
        .filter(messagingModule -> satisfiesCondition(messagingModule, filter))
        .collect(Collectors.toSet());
    }
    return modules;
  }

  private static boolean satisfiesCondition(MessagingModule module, MessagingModuleFilter filter) {
    boolean result = true;
    if (filter.getEventType() != null) {
      result = filter.getEventType().equals(module.getEventType());
    }
    if (filter.getModuleId() != null) {
      result = result && filter.getModuleId().equals(module.getModuleId());
    }
    if (filter.getTenantId() != null) {
      result = result && filter.getTenantId().equals(module.getTenantId());
    }
    if (filter.getModuleRole() != null) {
      result = result && filter.getModuleRole().equals(module.getModuleRole());
    }
    if (filter.getActivated() != null) {
      result = result && filter.getActivated().equals(module.getActivated());
    }
    if (filter.getSubscriberCallback() != null) {
      result = result && filter.getSubscriberCallback().equals(module.getSubscriberCallback());
    }
    return result;
  }
}
