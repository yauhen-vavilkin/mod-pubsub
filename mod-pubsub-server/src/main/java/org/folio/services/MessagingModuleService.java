package org.folio.services;

import io.vertx.core.Future;
import org.folio.dao.impl.MessagingModuleFilter;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.jaxrs.model.MessagingModuleCollection;
import org.folio.rest.jaxrs.model.PublisherDescriptor;

/**
 * Messaging Module service
 */
public interface MessagingModuleService {

  /**
   * Validates PublisherDescriptor
   * @param publisherDescriptor Publisher Descriptor
   * @return future with validation result
   */
  Future<Errors> validatePublisherDescriptor(PublisherDescriptor publisherDescriptor);

  /**
   * Creates publisher of event types specified in publisherDescriptor
   *
   * @param publisherDescriptor publisher descriptor
   * @param tenantId tenant id
   */
  Future<Boolean> savePublisher(PublisherDescriptor publisherDescriptor, String tenantId);

  /**
   * Deletes by module name and filter
   *
   * @param moduleName module name
   * @param filter MessagingModule filter
   * @return future with boolean
   */
  Future<Boolean> deleteByModuleNameAndFilter(String moduleName, MessagingModuleFilter filter);

  /**
   * Searches MessagingModule entities by event type and module role
   *
   * @param eventType event type
   * @param role role
   * @param tenantId tenant id
   * @return future with MessagingModule collection
   */
  Future<MessagingModuleCollection> getByEventTypeAndRole(String eventType, ModuleRole role, String tenantId);
}
