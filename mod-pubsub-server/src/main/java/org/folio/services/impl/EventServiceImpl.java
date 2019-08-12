package org.folio.services.impl;

import io.vertx.core.Future;
import org.folio.dao.EventDao;
import org.folio.rest.jaxrs.model.Event;
import org.folio.services.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

  @Autowired
  private EventDao eventDao;

  @Override
  public Future<Optional<Event>> getById(String eventId, String tenantId) {
    return eventDao.getById(eventId, tenantId); // NOSONAR
  }
}
