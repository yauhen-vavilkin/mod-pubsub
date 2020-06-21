package org.folio.services.impl;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.folio.dao.EventDescriptorDao;
import org.folio.dao.MessagingModuleDao;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.jaxrs.model.MessagingModuleCollection;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.model.SubscriptionDefinition;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.ConsumerService;
import org.folio.services.KafkaTopicService;
import org.folio.services.MessagingModuleService;
import org.folio.services.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

/**
 * Implementation for Messaging Module service
 *
 * @see MessagingModuleService
 */
@Component
public class MessagingModuleServiceImpl implements MessagingModuleService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MessagingModuleServiceImpl.class);

  private MessagingModuleDao messagingModuleDao;
  private EventDescriptorDao eventDescriptorDao;
  private KafkaTopicService kafkaTopicService;
  private ConsumerService consumerService;
  private Cache cache;

  public MessagingModuleServiceImpl(@Autowired MessagingModuleDao messagingModuleDao,
                                    @Autowired EventDescriptorDao eventDescriptorDao,
                                    @Autowired KafkaTopicService kafkaTopicService,
                                    @Autowired ConsumerService consumerService,
                                    @Autowired Cache cache) {
    this.messagingModuleDao = messagingModuleDao;
    this.eventDescriptorDao = eventDescriptorDao;
    this.kafkaTopicService = kafkaTopicService;
    this.consumerService = consumerService;
    this.cache = cache;
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

  @Override
  public Future<Boolean> createMissingEventTypes(SubscriberDescriptor subscriberDescriptor) {
    List<String> eventTypes = subscriberDescriptor.getSubscriptionDefinitions().stream()
      .map(SubscriptionDefinition::getEventType)
      .collect(Collectors.toList());

    return eventDescriptorDao.getByEventTypes(eventTypes)
      .map(existingDescriptorList -> {
        Map<String, EventDescriptor> descriptorsMap = existingDescriptorList.stream()
          .collect(Collectors.toMap(EventDescriptor::getEventType, descriptor -> descriptor));
        List<Future> futures = new ArrayList<>();
        for (String eventType : eventTypes) {
          if (descriptorsMap.get(eventType) == null) {
            LOGGER.info("Event type {} does not exist, creating a temporary definition", eventType);
            futures.add(eventDescriptorDao.save(
              new EventDescriptor()
                .withEventType(eventType)
                .withEventTTL(1)
                .withTmp(true)));
          }
        }
        return CompositeFuture.join(futures);
      }).map(true);
  }

  @Override
  public Future<Boolean> savePublisher(PublisherDescriptor publisherDescriptor, String tenantId) {
    List<String> eventTypes = publisherDescriptor.getEventDescriptors().stream()
      .map(EventDescriptor::getEventType).collect(Collectors.toList());
    List<MessagingModule> messagingModules = createMessagingModules(publisherDescriptor.getModuleId(), eventTypes, PUBLISHER, tenantId);
    if (messagingModules.isEmpty()) {
      LOGGER.info("List of Publishers is empty");
      return Future.succeededFuture(true);
    }

    return messagingModuleDao.save(messagingModules)
      .onSuccess(ar -> cache.invalidate())
      .compose(ar -> kafkaTopicService.createTopics(eventTypes, tenantId));
  }

  @Override
  public Future<Boolean> saveSubscriber(SubscriberDescriptor subscriberDescriptor, OkapiConnectionParams params) {
    List<String> eventTypes = subscriberDescriptor.getSubscriptionDefinitions().stream()
      .map(SubscriptionDefinition::getEventType)
      .collect(Collectors.toList());
    List<MessagingModule> messagingModules = createMessagingModules(subscriberDescriptor.getModuleId(), eventTypes, SUBSCRIBER, params.getTenantId());
    if (messagingModules.isEmpty()) {
      LOGGER.info("List of Subscribers is empty");
      return Future.succeededFuture(true);
    }

    Map<String, String> subscriberCallbacksMap = subscriberDescriptor.getSubscriptionDefinitions().stream()
      .collect(Collectors.toMap(SubscriptionDefinition::getEventType, SubscriptionDefinition::getCallbackAddress));
    messagingModules.forEach(module -> module.setSubscriberCallback(subscriberCallbacksMap.get(module.getEventType())));

    return messagingModuleDao.save(messagingModules)
      .onSuccess(ar -> cache.invalidate())
      .compose(ar -> kafkaTopicService.createTopics(eventTypes, params.getTenantId()))
      .compose(ar -> consumerService.subscribe(eventTypes, params));
  }

  @Override
  public Future<Boolean> delete(MessagingModuleFilter filter) {
    return messagingModuleDao.delete(filter)
      .onSuccess(ar -> cache.invalidate());
  }

  @Override
  public Future<MessagingModuleCollection> get(MessagingModuleFilter filter) {
    return messagingModuleDao.get(filter)
      .map(messagingModules -> new MessagingModuleCollection()
        .withMessagingModules(messagingModules)
        .withTotalRecords(messagingModules.size()));
  }

  private void compareEventDescriptors(EventDescriptor eventDescriptor, EventDescriptor existingDescriptor, Errors errors) {
    if (existingDescriptor == null) {
      String error = String.format("Event type '%s' does not exist", eventDescriptor.getEventType());
      LOGGER.error(error);
      errors.getErrors().add(new Error().withMessage(error));
    } else {
      if (!EqualsBuilder.reflectionEquals(eventDescriptor, existingDescriptor)) {
        String descriptorContent = JsonObject.mapFrom(existingDescriptor).encodePrettily();
        String message = String.format(
          "Publisher descriptor does not match existing descriptor for event type '%s'. To declare a publisher one should use the following descriptor: %s",
          eventDescriptor.getEventType(), descriptorContent);
        LOGGER.error(message);
        errors.getErrors().add(new Error().withMessage(message));
      }
    }
    errors.setTotalRecords(errors.getErrors().size());
  }

  /**
   * Creates Messaging Modules by event type and role
   *
   * @param moduleId   module id
   * @param eventTypes event types list
   * @param moduleRole MessagingModule role
   * @param tenantId   tenant id
   * @return Messaging Modules list
   */
  private List<MessagingModule> createMessagingModules(String moduleId, List<String> eventTypes, ModuleRole moduleRole, String tenantId) {
    return eventTypes.stream()
      .map(eventType -> createMessagingModule(moduleId, eventType, moduleRole, tenantId))
      .collect(Collectors.toList());
  }

  /**
   * Creates Messaging Module by event type and role
   *
   * @param moduleId   module id
   * @param eventType  event type name
   * @param moduleRole module role
   * @param tenantId   tenant id
   * @return MessagingModule
   */
  private MessagingModule createMessagingModule(String moduleId, String eventType, ModuleRole moduleRole, String tenantId) {
    return new MessagingModule()
      .withId(UUID.randomUUID().toString())
      .withModuleId(moduleId)
      .withTenantId(tenantId)
      .withEventType(eventType)
      .withModuleRole(moduleRole)
      .withActivated(true);
  }
}
