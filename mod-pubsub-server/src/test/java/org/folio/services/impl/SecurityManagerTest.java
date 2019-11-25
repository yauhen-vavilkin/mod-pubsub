package org.folio.services.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.PubSubUserDao;
import org.folio.dao.impl.PubSubUserDaoImpl;
import org.folio.rest.impl.AbstractRestTest;
import org.folio.rest.util.OkapiConnectionParams;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.UUID;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class SecurityManagerTest extends AbstractRestTest {

  private static final String LOGIN_URL = "/authn/login";
  private static final String TENANT = "diku";
  private static final String TOKEN = "token";

  @Spy
  PostgresClientFactory postgresClientFactory = new PostgresClientFactory(Vertx.vertx());
  @InjectMocks
  private PubSubUserDao pubSubUserDao = new PubSubUserDaoImpl();
  @Spy
  private SecurityManagerImpl securityManager = new SecurityManagerImpl(pubSubUserDao);

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void shouldLoginPubSubUser(TestContext context) {
    Async async = context.async();

    String pubSubToken = UUID.randomUUID().toString();

    WireMock.stubFor(WireMock.post(LOGIN_URL)
      .willReturn(WireMock.created().withHeader(OKAPI_HEADER_TOKEN, pubSubToken)));

    OkapiConnectionParams params = new OkapiConnectionParams();
    params.setVertx(vertx);
    params.setOkapiUrl("http://localhost:" + mockServer.port());
    params.setTenantId(TENANT);
    params.setToken(TOKEN);

    Future<String> future = securityManager.loginPubSubUser(params)
      .compose(ar -> securityManager.getJWTToken(params.getTenantId()));

    future.setHandler(ar -> {
      assertTrue(ar.succeeded());
      assertEquals(pubSubToken, ar.result());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      assertEquals(1, requests.size());
      assertEquals(LOGIN_URL, requests.get(0).getUrl());
      assertEquals("POST", requests.get(0).getMethod().getName());
      async.complete();
    });
  }

}
