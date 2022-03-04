package org.folio.dao.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.ws.rs.NotFoundException;

import org.folio.dao.PostgresClientFactory;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Tuple;

@RunWith(VertxUnitRunner.class)
public class EventDescriptorDaoImplTest {

  @Mock
  private PostgresClientFactory postgresClientFactory;

  @Mock
  private PostgresClient postgresClient;

  @InjectMocks
  EventDescriptorDaoImpl eventDescriptorDao = new EventDescriptorDaoImpl();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(postgresClientFactory.getInstance()).thenReturn(postgresClient);
  }

  private void shouldSucceedOnDelete(TestContext context, int rowCount) {
    // given
    when(postgresClient.execute(anyString(), any(Tuple.class)))
      .thenReturn(Future.succeededFuture(new LocalRowSet(rowCount)));
    // when
    eventDescriptorDao.delete("event-type")
    // then
    .onComplete(context.asyncAssertSuccess(x ->
      verify(postgresClient).execute(anyString(), any(Tuple.class))));
  }
  @Test(expected = NotFoundException.class)
  public void shouldFailOnDeleteMoreThanOneRow(TestContext context) {
    shouldSucceedOnDelete(context, 2);
  }

  @Test
  public void shouldSucceedOnDeleteExisting(TestContext context) {
    shouldSucceedOnDelete(context, 1);
  }

  @Test(expected = NotFoundException.class)
  public void shouldFailOnDeleteNotFound(TestContext context) {
    shouldSucceedOnDelete(context, 0);
  }
}
