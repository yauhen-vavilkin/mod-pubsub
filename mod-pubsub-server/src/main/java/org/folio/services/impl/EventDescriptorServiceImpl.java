package org.folio.services.impl;

import io.vertx.core.Future;
import org.folio.dao.EventDescriptorDao;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.EventDescriptorCollection;
import org.folio.services.EventDescriptorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Optional;

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
  public Future<Optional<EventDescriptor>> getById(String id) {
    return eventDescriptorDao.getById(id);
  }

  @Override
  public Future<String> save(EventDescriptor eventDescriptor) {
    return eventDescriptorDao.save(eventDescriptor);
  }

  @Override
  public Future<EventDescriptor> update(EventDescriptor eventDescriptor) {
    return eventDescriptorDao.update(eventDescriptor);
  }

  @Override
  public Future<Boolean> delete(String id) {
    return eventDescriptorDao.getById(id)
      .compose(eventDescriptorOptional -> eventDescriptorOptional
        .map(eventDescriptor -> eventDescriptorDao.delete(id))
        .orElse(Future.failedFuture(new NotFoundException(String.format("EventDescriptor with event type name '%s' was not found", id)))));
  }
}
