package org.folio.dao.impl;

import io.vertx.core.Future;
import org.folio.dao.EventDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EventDaoImpl implements EventDao {

  @Autowired
  private PostgresClientFactory pgClientFactory; // NOSONAR

  @Override
  public Future<Optional<Event>> getById(String eventId, String tenantId) {
    /*
      Implementation is stubbed.
      Use PostgresClientFactory to obtain PostgresClient instance and
      query for Event entity from the underlying database.
    */
    return Future.succeededFuture(Optional.of(new Event().withId(eventId)));
  }
}
