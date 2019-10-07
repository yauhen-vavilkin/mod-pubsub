package org.folio.dao.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.ModuleDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.Module;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class ModuleDaoImplUnitTest {

  private Module module = new Module()
    .withId(UUID.randomUUID().toString())
    .withName("test_module");

  @Mock
  private PostgresClientFactory postgresClientFactory;

  @Mock
  private PostgresClient pgClient;

  @InjectMocks
  private ModuleDao moduleDao = new ModuleDaoImpl();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(postgresClientFactory.getInstance())
      .thenReturn(pgClient);
  }

  @Test
  public void shouldReturnFutureWithListOfModuleOnSuccessfulSelectionAll(TestContext context) {
    // given
    Async async = context.async();
    JsonArray rowAsJsonArray = new JsonArray().add(module.getId()).add(module.getName());
    ResultSet resultSet = new ResultSet();
    resultSet.setColumnNames(Arrays.asList("id", "name"));
    resultSet.setResults(Collections.singletonList(rowAsJsonArray));

    doAnswer(invocation -> {
      Handler<AsyncResult<ResultSet>> handler = invocation.getArgument(1);
      handler.handle(Future.succeededFuture(resultSet));
      return 0;
    })
      .when(pgClient).select(anyString(), any(Handler.class));
    // when
    moduleDao.getAll()
    // then
      .setHandler(ar -> {
        Assert.assertTrue(ar.succeeded());
        List<Module> moduleList = ar.result();
        Assert.assertThat(moduleList.size(), is(1));
        Module selectedModule = moduleList.get(0);
        Assert.assertThat(selectedModule.getId(), is(module.getId()));
        Assert.assertThat(selectedModule.getName(), is(module.getName()));
        verify(pgClient).select(anyString(), any(Handler.class));
        async.complete();
      });
  }

  @Test
  public void shouldReturnFutureWithEmptyListOfModuleWhenResultSetReturnsEmptyListOfRows(TestContext context) {
    // given
    Async async = context.async();
    ResultSet resultSet = new ResultSet();
    resultSet.setColumnNames(Arrays.asList("id", "name"));
    resultSet.setResults(Collections.emptyList());

    doAnswer(invocation -> {
      Handler<AsyncResult<ResultSet>> handler = invocation.getArgument(1);
      handler.handle(Future.succeededFuture(resultSet));
      return 0;
    })
      .when(pgClient).select(anyString(), any(Handler.class));
    // when
    moduleDao.getAll()
    // then
      .setHandler(ar -> {
        Assert.assertTrue(ar.succeeded());
        List<Module> moduleList = ar.result();
        Assert.assertThat(moduleList.size(), is(0));
        verify(pgClient).select(anyString(), any(Handler.class));
        async.complete();
      });
  }

  @Test
  public void shouldReturnFutureWithTrueOnSuccessfulDeletionById(TestContext context) {
    // given
    Async async = context.async();
    int updatedRowsNumber = 1;
    UpdateResult updateResult = new UpdateResult();
    updateResult.setUpdated(updatedRowsNumber);
    JsonArray queryParams = new JsonArray().add(module.getId());

    doAnswer(invocation -> {
      Handler<AsyncResult<UpdateResult>> handler = invocation.getArgument(2);
      handler.handle(Future.succeededFuture(updateResult));
      return 0;
    })
    .when(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
    // when
    moduleDao.delete(module.getId())
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
    JsonArray queryParams = new JsonArray().add(module.getId());

    doAnswer(invocation -> {
      Handler<AsyncResult<UpdateResult>> handler = invocation.getArgument(2);
      handler.handle(Future.succeededFuture(updateResult));
      return 0;
    })
      .when(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
    // when
    moduleDao.delete(module.getId())
    // then
      .setHandler(ar -> {
        Assert.assertTrue(ar.succeeded());
        Assert.assertEquals(false, ar.result());
        verify(pgClient).execute(anyString(), eq(queryParams), any(Handler.class));
        async.complete();
      });
  }

}
