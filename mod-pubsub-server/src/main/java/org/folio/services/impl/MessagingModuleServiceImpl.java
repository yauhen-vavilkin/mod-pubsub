package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.dao.EventDescriptorDao;
import org.folio.dao.MessagingModuleDao;
import org.folio.dao.impl.MessagingModuleFilter;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.jaxrs.model.MessagingModuleCollection;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.model.SubscriptionDefinition;
import org.folio.services.KafkaTopicService;
import org.folio.services.MessagingModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

/**
 * Implementation for Messaging Module service
 *
 * @see org.folio.services.MessagingModuleService
 */
@Component
public class MessagingModuleServiceImpl implements MessagingModuleService {

  private static final int NUMBER_OF_PARTITIONS = 1;
  private static final short REPLICATION_FACTOR = 1;
  private MessagingModuleDao messagingModuleDao;
  private EventDescriptorDao eventDescriptorDao;
  private KafkaTopicService kafkaTopicService;

  public MessagingModuleServiceImpl(@Autowired MessagingModuleDao messagingModuleDao,
                                    @Autowired EventDescriptorDao eventDescriptorDao,
                                    @Autowired KafkaTopicService kafkaTopicService) {
    this.messagingModuleDao = messagingModuleDao;
    this.eventDescriptorDao = eventDescriptorDao;
    this.kafkaTopicService = kafkaTopicService;
  }

  @Override
  public Future<Errors> validatePublisherDescriptor(PublisherDescriptor publisherDescriptor) {
    Errors errors = new Errors().withTotalRecords(0);
    List<EventDescriptor> eventDescriptors = publisherDescriptor.getEventDescriptors();
    List<String> eventTypes = eventDescriptors.stream().map(EventDescriptor::getEventType).collect(Collectors.toList());

    return eventDescriptorDao.getByEventTypes(eventTypes)
      .map(existingDescriptorList -> {
        Map<String, EventDescriptor> descriptorsMap = existingDescriptorList.stream()
          .collect(Collectors.toMap(EventDescriptor::getEventType, descriptor -> descriptor));
        for (EventDescriptor eventDescriptor : eventDescriptors) {
          EventDescriptor existingDescriptor = descriptorsMap.get(eventDescriptor.getEventType());
          compareEventDescriptors(eventDescriptor, existingDescriptor, errors);
        }
        return errors.withTotalRecords(errors.getErrors().size());
      });
  }

  private void compareEventDescriptors(EventDescriptor eventDescriptor, EventDescriptor existingDescriptor, Errors errors) {
    if (existingDescriptor == null) {
      errors.getErrors().add(new Error().withMessage(String.format("Event type '%s' does not exist", eventDescriptor.getEventType())));
    } else {
      JsonObject descriptorJson = JsonObject.mapFrom(eventDescriptor);
      JsonObject existingDescriptorJson = JsonObject.mapFrom(existingDescriptor);
      if (!descriptorJson.equals(existingDescriptorJson)) {
        String descriptorContent = JsonObject.mapFrom(existingDescriptor).encodePrettily();
        String message = String.format("Descriptor of event type '%s' does not match to existing descriptor. To declare publisher should use follow descriptor: %s",
          eventDescriptor.getEventType(), descriptorContent);
        errors.getErrors().add(new Error().withMessage(message));
      }
    }
    errors.setTotalRecords(errors.getErrors().size());
  }

  @Override
  public Future<Errors> validateSubscriberDescriptor(SubscriberDescriptor subscriberDescriptor) {
    Errors errors = new Errors();
    List<String> eventTypes = subscriberDescriptor.getSubscriptionDefinitions().stream()
      .map(SubscriptionDefinition::getEventType)
      .collect(Collectors.toList());

    return eventDescriptorDao.getByEventTypes(eventTypes).map(existingDescriptorList -> {
      Map<String, EventDescriptor> descriptorsMap = existingDescriptorList.stream()
        .collect(Collectors.toMap(EventDescriptor::getEventType, descriptor -> descriptor));
      for (String eventType : eventTypes) {
        if (descriptorsMap.get(eventType) == null) {
          errors.getErrors().add(new Error().withMessage(String.format("Event type '%s' does not exist", eventType)));
        }
      }
      return errors.withTotalRecords(errors.getErrors().size());
    });
  }

  @Override
  public Future<Boolean> savePublisher(PublisherDescriptor publisherDescriptor, String tenantId) {
    List<String> eventTypes = publisherDescriptor.getEventDescriptors().stream()
      .map(EventDescriptor::getEventType).collect(Collectors.toList());
    List<MessagingModule> messagingModules = createMessagingModules(eventTypes, PUBLISHER, tenantId);

    return messagingModuleDao.save(publisherDescriptor.getModuleName(), messagingModules)
      .compose(ar -> kafkaTopicService.createTopics(eventTypes, tenantId, NUMBER_OF_PARTITIONS, REPLICATION_FACTOR));
  }

  @Override
  public Future<Boolean> saveSubscriber(SubscriberDescriptor subscriberDescriptor, String tenantId) {
    List<String> eventTypes = subscriberDescriptor.getSubscriptionDefinitions().stream()
      .map(SubscriptionDefinition::getEventType)
      .collect(Collectors.toList());
    List<MessagingModule> messagingModules = createMessagingModules(eventTypes, SUBSCRIBER, tenantId);

    Map<String, String> subscriberCallbacksMap = subscriberDescriptor.getSubscriptionDefinitions().stream()
      .collect(Collectors.toMap(SubscriptionDefinition::getEventType, SubscriptionDefinition::getCallbackAddress));
    messagingModules.forEach(module -> module.setSubscriberCallback(subscriberCallbacksMap.get(module.getEventType())));

    return messagingModuleDao.save(subscriberDescriptor.getModuleName(), messagingModules)
      .compose(ar -> kafkaTopicService.createTopics(eventTypes, tenantId, NUMBER_OF_PARTITIONS, REPLICATION_FACTOR));
  }

  /**
   * Creates Messaging Modules by event type and role
   *
   * @param eventTypes event types list
   * @param moduleRole MessagingModule role
   * @param tenantId tenant id
   * @return Messaging Modules list
   */
  private List<MessagingModule> createMessagingModules(List<String> eventTypes, ModuleRole moduleRole, String tenantId) {
    return eventTypes.stream()
      .map(eventType -> createMessagingModule(eventType, moduleRole, tenantId))
      .collect(Collectors.toList());
  }

  /**
   * Creates Messaging Module by event type and role
   *
   * @param eventType event type name
   * @param moduleRole module role
   * @param tenantId tenant id
   * @return MessagingModule
   */
  private MessagingModule createMessagingModule(String eventType, ModuleRole moduleRole, String tenantId) {
    return new MessagingModule()
      .withId(UUID.randomUUID().toString())
      .withTenantId(tenantId)
      .withEventType(eventType)
      .withModuleRole(moduleRole)
      .withApplied(true);
  }

  @Override
  public Future<Boolean> deleteByModuleNameAndFilter(String moduleName, MessagingModuleFilter filter) {
    return messagingModuleDao.deleteByModuleNameAndFilter(moduleName, filter);
  }

  @Override
  public Future<MessagingModuleCollection> getByEventTypeAndRole(String eventType, ModuleRole role, String tenantId) {
    MessagingModuleFilter messagingModuleFilter = new MessagingModuleFilter();
    messagingModuleFilter.byEventType(eventType);
    messagingModuleFilter.byModuleRole(role);
    messagingModuleFilter.byTenantId(tenantId);
    return messagingModuleDao.get(messagingModuleFilter)
      .map(messagingModules -> new MessagingModuleCollection()
        .withMessagingModules(messagingModules)
        .withTotalRecords(messagingModules.size()));
  }

}
