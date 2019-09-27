package org.folio.services;

import io.vertx.core.Future;
import org.folio.dao.impl.MessagingModuleFilter;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.jaxrs.model.MessagingModuleCollection;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;

/**
 * Messaging Module service
 */
public interface MessagingModuleService {

  /**
   * Validates PublisherDescriptor
   *
   * @param publisherDescriptor publisher descriptor
   * @return future with validation result
   */
  Future<Errors> validatePublisherDescriptor(PublisherDescriptor publisherDescriptor);

  /**
   * Creates publisher of event types specified in publisherDescriptor
   *
   * @param publisherDescriptor publisher descriptor
   * @param tenantId tenant id
   * @return future with boolean
   */
  Future<Boolean> savePublisher(PublisherDescriptor publisherDescriptor, String tenantId);

  /**
   * Validates SubscriberDescriptor
   *
   * @param subscriberDescriptor subscriber descriptor
   * @return future with validation result
   */
  Future<Errors> validateSubscriberDescriptor(SubscriberDescriptor subscriberDescriptor);

  /**
   * Creates subscriber of event types specified in subscriberDescriptor
   *
   * @param subscriberDescriptor subscriber descriptor
   * @param tenantId tenant id
   * @return future with boolean
   */
  Future<Boolean> saveSubscriber(SubscriberDescriptor subscriberDescriptor, String tenantId);

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
