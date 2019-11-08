package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.folio.dao.EventDescriptorDao;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.EventDescriptorCollection;
import org.folio.services.EventDescriptorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Implementation for Event Descriptor service
 *
 * @see org.folio.services.EventDescriptorService
 */
@Component
public class EventDescriptorServiceImpl implements EventDescriptorService {

  private EventDescriptorDao eventDescriptorDao;

  public EventDescriptorServiceImpl(@Autowired EventDescriptorDao eventDescriptorDao) {
    this.eventDescriptorDao = eventDescriptorDao;
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
    return eventDescriptorDao.getByEventType(eventDescriptor.getEventType())
      .compose(eventDescriptorOptional -> {
        if (eventDescriptorOptional.isPresent()) {
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
        .map(eventDescriptor -> eventDescriptorDao.delete(eventType))
        .orElse(Future.failedFuture(new NotFoundException(String.format("EventDescriptor with event type name '%s' was not found", eventType)))));
  }

}
