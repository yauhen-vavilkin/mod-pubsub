package org.folio.services.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.MessagingModuleDao;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.PubSubUserDao;
import org.folio.dao.impl.MessagingModuleDaoImpl;
import org.folio.dao.impl.PubSubUserDaoImpl;
import org.folio.rest.impl.AbstractRestTest;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.cache.Cache;
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

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class SecurityManagerTest extends AbstractRestTest {

  private static final String LOGIN_URL = "/authn/login";
  private static final String USERS_URL = "/users";
  private static final String USERS_URL_WITH_QUERY = "/users?query=username=pub-sub";
  private static final String CREDENTIALS_URL = "/authn/credentials";
  private static final String PERMISSIONS_URL = "/perms/users";
  private static final String TENANT = "diku";
  private static final String TOKEN = "token";
  private static final String TOKEN_KEY_FORMAT = "%s_JWTToken";

  private Map<String, String> headers = new HashMap<>();

  @Spy
  private Vertx vertx = Vertx.vertx();
  @Spy
  PostgresClientFactory postgresClientFactory = new PostgresClientFactory(vertx);
  @InjectMocks
  private PubSubUserDao pubSubUserDao = new PubSubUserDaoImpl();
  @InjectMocks
  private MessagingModuleDao messagingModuleDao = new MessagingModuleDaoImpl();
  private Cache cache = new Cache(vertx, messagingModuleDao);
  @Spy
  private SecurityManagerImpl securityManager = new SecurityManagerImpl(pubSubUserDao, vertx, cache);

  private Context vertxContext = vertx.getOrCreateContext();

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(vertx.getOrCreateContext()).thenReturn(vertxContext);

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
      .compose(ar -> securityManager.getJWTToken(params));

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(pubSubToken, ar.result());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      context.assertEquals(1, requests.size());
      context.assertEquals(LOGIN_URL, requests.get(0).getUrl());
      context.assertEquals("POST", requests.get(0).getMethod().getName());
      String actualToken = cache.getToken(params.getTenantId());
      context.assertEquals(pubSubToken, actualToken);
      async.complete();
    });
  }

  @Test
  public void shouldNotCreatePubSubUserIfItExists(TestContext context) {
    Async async = context.async();

    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray()
        .add(new JsonObject()
          .put("username", "pub-sub")
          .put("id", userId)))
      .put("totalRecords", 1).encode();
    String permUrl = PERMISSIONS_URL + "/" + userId + "/permissions?indexField=userId";

    WireMock.stubFor(WireMock.get(USERS_URL_WITH_QUERY)
      .willReturn(WireMock.ok().withBody(userCollection)));
    WireMock.stubFor(WireMock.post(permUrl)
      .willReturn(WireMock.ok()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Boolean> future = securityManager.createPubSubUser(params);

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertTrue(ar.result());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      context.assertEquals(2, requests.size());
      context.assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      context.assertEquals("GET", requests.get(0).getMethod().getName());
      context.assertEquals(permUrl, requests.get(1).getUrl());
      context.assertEquals("POST", requests.get(1).getMethod().getName());
      async.complete();
    });
  }

  @Test
  public void shouldCreatePubSubUser(TestContext context) {
    Async async = context.async();

    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray()
        .add(new JsonObject()
          .put("username", "pub-sub")
          .put("id", userId)))
      .put("totalRecords", 1).encode();

    WireMock.stubFor(WireMock.get(USERS_URL_WITH_QUERY)
      .willReturn(WireMock.ok().withBody(new JsonObject().put("users", new JsonArray()).encode())));
    WireMock.stubFor(WireMock.post(USERS_URL)
      .willReturn(WireMock.created().withBody(userCollection)));
    WireMock.stubFor(WireMock.post(CREDENTIALS_URL)
      .willReturn(WireMock.created()));
    WireMock.stubFor(WireMock.post(PERMISSIONS_URL)
      .willReturn(WireMock.created()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Boolean> future = securityManager.createPubSubUser(params);

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      assertTrue(ar.result());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      context.assertEquals(4, requests.size());
      context.assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      context.assertEquals("GET", requests.get(0).getMethod().getName());
      context.assertEquals(USERS_URL, requests.get(1).getUrl());
      context.assertEquals("POST", requests.get(1).getMethod().getName());
      context.assertEquals(CREDENTIALS_URL, requests.get(2).getUrl());
      context.assertEquals("POST", requests.get(2).getMethod().getName());
      context.assertEquals(PERMISSIONS_URL, requests.get(3).getUrl());
      context.assertEquals("POST", requests.get(3).getMethod().getName());
      async.complete();
    });
  }

  @Test
  public void shouldLoginPubSubUserWhenContextHasNoToken(TestContext context) {
    Async async = context.async();
    String expectedToken = UUID.randomUUID().toString();

    WireMock.stubFor(WireMock.post(LOGIN_URL)
      .willReturn(WireMock.created().withHeader(OKAPI_HEADER_TOKEN, expectedToken)));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<String> future = securityManager.getJWTToken(params);

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(expectedToken, ar.result());
      verify(1, postRequestedFor(urlEqualTo(LOGIN_URL)));
      async.complete();
    });
  }

}
