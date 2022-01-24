package org.folio.dao.impl;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.util.MessagingModuleFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class MessagingModuleDaoImplUnitTest {

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

  private void shouldSucceedOnDelete(TestContext context, int rowCount) {
    // given
    when(pgClient.execute(anyString()))
        .thenReturn(Future.succeededFuture(new LocalRowSet(rowCount)));
    // when
    messagingModuleDao.delete(new MessagingModuleFilter())
    // then
    .onComplete(context.asyncAssertSuccess(x -> {
      verify(pgClient).execute(anyString());
    }));
  }

  @Test
  public void shouldSucceedOnDeleteExisting(TestContext context) {
    shouldSucceedOnDelete(context, 1);
  }

  @Test
  public void shouldSucceedOnDeleteNotFound(TestContext context) {
    shouldSucceedOnDelete(context, 0);
  }

}
