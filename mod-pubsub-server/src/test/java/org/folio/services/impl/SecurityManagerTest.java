package org.folio.services.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class SecurityManagerTest extends AbstractRestTest {

  private static final String LOGIN_URL = "/authn/login";
  private static final String USERS_URL = "/users";
  private static final String USERS_URL_WITH_QUERY = "/users?query=username=pub-sub";
  private static final String CREDENTIALS_URL = "/authn/credentials";
  private static final String PERMISSIONS_URL = "/perms/users";
  private static final String TENANT = "diku";
  private static final String TOKEN = "token";

  private Map<String, String> headers = new HashMap<>();
  private Vertx vertx = Vertx.vertx();

  @Spy
  PostgresClientFactory postgresClientFactory = new PostgresClientFactory(vertx);
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
    headers.put(OKAPI_URL_HEADER, "http://localhost:" + mockServer.port());
    headers.put(OKAPI_TENANT_HEADER, TENANT);
    headers.put(OKAPI_TOKEN_HEADER, TOKEN);
  }

  @Test
  public void shouldLoginPubSubUser(TestContext context) {
    Async async = context.async();

    String pubSubToken = UUID.randomUUID().toString();

    WireMock.stubFor(WireMock.post(LOGIN_URL)
      .willReturn(WireMock.created().withHeader(OKAPI_HEADER_TOKEN, pubSubToken)));

    OkapiConnectionParams params = new OkapiConnectionParams();
    params.setVertx(vertx);
    params.setOkapiUrl(headers.get(OKAPI_URL_HEADER));
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

  @Test
  public void shouldNotCreatePubSubUserIfItExists(TestContext context) {
    Async async = context.async();

    WireMock.stubFor(WireMock.get(USERS_URL_WITH_QUERY)
      .willReturn(WireMock.ok().withBody(new JsonObject().put("totalRecords", 1).encode())));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Boolean> future = securityManager.createPubSubUser(params);

    future.setHandler(ar -> {
      assertTrue(ar.succeeded());
      assertTrue(ar.result());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      assertEquals(1, requests.size());
      assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      assertEquals("GET", requests.get(0).getMethod().getName());
      async.complete();
    });
  }

  @Test
  public void shouldCreatePubSubUser(TestContext context) {
    Async async = context.async();

    WireMock.stubFor(WireMock.get(USERS_URL_WITH_QUERY)
      .willReturn(WireMock.ok().withBody(new JsonObject().put("totalRecords", 0).encode())));
    WireMock.stubFor(WireMock.post(USERS_URL)
      .willReturn(WireMock.created()));
    WireMock.stubFor(WireMock.post(CREDENTIALS_URL)
      .willReturn(WireMock.created()));
    WireMock.stubFor(WireMock.post(PERMISSIONS_URL)
      .willReturn(WireMock.created()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Boolean> future = securityManager.createPubSubUser(params);

    future.setHandler(ar -> {
      assertTrue(ar.succeeded());
      assertTrue(ar.result());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      assertEquals(4, requests.size());
      assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      assertEquals("GET", requests.get(0).getMethod().getName());
      assertEquals(USERS_URL, requests.get(1).getUrl());
      assertEquals("POST", requests.get(1).getMethod().getName());
      assertEquals(CREDENTIALS_URL, requests.get(2).getUrl());
      assertEquals("POST", requests.get(2).getMethod().getName());
      assertEquals(PERMISSIONS_URL, requests.get(3).getUrl());
      assertEquals("POST", requests.get(3).getMethod().getName());
      async.complete();
    });
  }

}
