package org.folio.dao.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class MessagingModuleDaoImplUnitTest {

  private MessagingModule messagingModule = new MessagingModule()
    .withId(UUID.randomUUID().toString());

  @Mock
  private PostgresClientFactory postgresClientFactory;

  @Mock
  private PostgresClient pgClient;

  @InjectMocks
  private MessagingModuleDaoImpl messagingModuleDao = new MessagingModuleDaoImpl();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(postgresClientFactory.getInstance())
      .thenReturn(pgClient);
  }

  @Test
  public void shouldReturnFutureWithTrueOnSuccessfulDeletionById(TestContext context) {
    // given
    Async async = context.async();
    int updatedRowsNumber = 1;
    UpdateResult updateResult = new UpdateResult();
    updateResult.setUpdated(updatedRowsNumber);
    JsonArray queryParams = new JsonArray().add(messagingModule.getId());

    doAnswer(invocation -> {
      Handler<AsyncResult<UpdateResult>> handler = invocation.getArgument(2);
      handler.handle(Future.succeededFuture(updateResult));
      return updateResult;
    })
      .when(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
    // when
    messagingModuleDao.delete(messagingModule.getId())
    // then
      .setHandler(ar -> {
        Assert.assertTrue(ar.succeeded());
        Assert.assertEquals(true, ar.result());
        verify(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
        async.complete();
      });
  }

  @Test
  public void shouldReturnFutureWithFalseWhenEntityWithSpecifiedIdNotFound(TestContext context) {
    // given
    Async async = context.async();
    int updatedRowsNumber = 0;
    UpdateResult updateResult = new UpdateResult();
    updateResult.setUpdated(updatedRowsNumber);
    JsonArray queryParams = new JsonArray().add(messagingModule.getId());

    doAnswer(invocation -> {
      Handler<AsyncResult<UpdateResult>> handler = invocation.getArgument(2);
      handler.handle(Future.succeededFuture(updateResult));
      return updateResult;
    })
      .when(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
    // when
    messagingModuleDao.delete(messagingModule.getId())
    // then
      .setHandler(ar -> {
        Assert.assertTrue(ar.succeeded());
        Assert.assertEquals(false, ar.result());
        verify(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
        async.complete();
      });
  }
}
