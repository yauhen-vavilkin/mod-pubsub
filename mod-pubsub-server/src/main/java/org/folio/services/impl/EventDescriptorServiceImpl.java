package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang3.StringUtils;
import org.folio.dao.EventDescriptorDao;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.EventDescriptorCollection;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.services.EventDescriptorService;
import org.folio.services.MessagingModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Implementation for Event Descriptor service
 *
 * @see org.folio.services.EventDescriptorService
 */
@Component
public class EventDescriptorServiceImpl implements EventDescriptorService {

  private EventDescriptorDao eventDescriptorDao;
  private MessagingModuleService messagingModuleService;

  public EventDescriptorServiceImpl(@Autowired EventDescriptorDao eventDescriptorDao,
                                    @Autowired MessagingModuleService messagingModuleService) {
    this.eventDescriptorDao = eventDescriptorDao;
    this.messagingModuleService = messagingModuleService;
  }

  @Override
  public Future<EventDescriptorCollection> getAll() {
    return eventDescriptorDao.getAll()
      .map(eventDescriptors -> new EventDescriptorCollection()
        .withEventDescriptors(eventDescriptors)
        .withTotalRecords(eventDescriptors.size()));
  }

  @Override
  public Future<Optional<EventDescriptor>> getByEventType(String eventType) {
    return eventDescriptorDao.getByEventType(eventType);
  }

  @Override
  public Future<String> save(EventDescriptor eventDescriptor) {
    if (eventDescriptor.getTmp() == null) {
      eventDescriptor.setTmp(false);
    }
    return eventDescriptorDao.getByEventType(eventDescriptor.getEventType())
      .compose(eventDescriptorOptional -> {
        if (eventDescriptorOptional.isPresent()) {
          if (eventDescriptorOptional.get().getTmp()){
            return eventDescriptorDao.update(eventDescriptor).map(EventDescriptor::getEventType);
          }
          if (EqualsBuilder.reflectionEquals(eventDescriptor, eventDescriptorOptional.get())) {
            return Future.succeededFuture(format("Event descriptor for event type '%s' is registered", eventDescriptor.getEventType()));
          } else {
            String descriptorContent = JsonObject.mapFrom(eventDescriptorOptional.get()).encodePrettily();
            return Future.failedFuture(new BadRequestException(
              format("Event descriptor for event type '%s' already exists, but the content is different. Existing event descriptor: %s",
                eventDescriptor.getEventType(), descriptorContent)));
          }
        } else {
          return eventDescriptorDao.save(eventDescriptor);
        }
      });
  }

  @Override
  public Future<EventDescriptor> update(EventDescriptor eventDescriptor) {
    return eventDescriptorDao.update(eventDescriptor);
  }

  @Override
  public Future<Boolean> delete(String eventType) {
    return eventDescriptorDao.getByEventType(eventType)
      .compose(eventDescriptorOptional -> eventDescriptorOptional
        .map(eventDescriptor -> messagingModuleService.get(new MessagingModuleFilter().withEventType(eventType))
          .compose(messagingModuleCollection -> {
            if (messagingModuleCollection.getTotalRecords() == 0) {
              return eventDescriptorDao.delete(eventType);
            } else {
              List<String> modules = messagingModuleCollection.getMessagingModules().stream().map(MessagingModule::getModuleId).collect(Collectors.toList());
              return Future.failedFuture(new BadRequestException(
                format("Event type %s cannot be deleted. Modules [%s] are registered as publishers or subscribers for this event type.", eventType,
                  StringUtils.join(modules, ","))));
            }
          })
        )
        .orElse(Future.failedFuture(new NotFoundException(format("EventDescriptor with event type name '%s' was not found", eventType)))));
  }

}
