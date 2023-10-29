package org.folio.services.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;
import static io.vertx.core.json.Json.decodeValue;
import static java.lang.String.format;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.folio.config.user.SystemUserConfig;
import org.folio.dao.MessagingModuleDao;
import org.folio.dao.impl.MessagingModuleDaoImpl;
import org.folio.representation.User;
import org.folio.rest.util.ExpiryAwareToken;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.cache.Cache;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

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

@RunWith(VertxUnitRunner.class)
public class SecurityManagerTest {
  protected static final String SYSTEM_USER_NAME = "test-pubsub-username";
  protected static final String SYSTEM_USER_PASSWORD = "test-pubsub-password";
  protected static final String SYSTEM_USER_TYPE = "system";

  private static final String LOGIN_URL = "/authn/login-with-expiry";
  private static final String USERS_URL = "/users";
  private static final String USERS_URL_WITH_QUERY = "/users?query=username=" + SYSTEM_USER_NAME;
  private static final String CREDENTIALS_URL = "/authn/credentials";
  private static final String PERMISSIONS_URL = "/perms/users";
  private static final String TENANT = "diku";

//  private static final ExpiryAwareToken TOKEN = new ExpiryAwareToken("token", 600);

  long TOKEN_MAX_AGE_SHORT = 2;
  long TOKEN_MAX_AGE = 600;
  long TOKEN_MAX_AGE_LONG = 604800;
  String ACCESS_TOKEN = UUID.randomUUID().toString();
  String REFRESH_TOKEN = UUID.randomUUID().toString();
  String ACCESS_TOKEN_COOKIE = format("folioAccessToken=%s; Max-Age=%d; Expires=Thu, 03 Aug 2023" +
    " 19:54:44 GMT; Path=/; Secure; HTTPOnly; SameSite=None", ACCESS_TOKEN, TOKEN_MAX_AGE);
  String REFRESH_TOKEN_COOKIE = format("folioRefreshToken=%s; Max-Age=%d; Expires=Thu, 10 Aug" +
      " 2023 19:44:44 GMT; Path=/authn; Secure; HTTPOnly; SameSite=None", REFRESH_TOKEN,
    TOKEN_MAX_AGE_LONG);
  String ACCESS_TOKEN_COOKIE_SHORT = format("folioAccessToken=%s; Max-Age=%d; Expires=Thu, 03 Aug" +
    " 2023 19:54:44 GMT; Path=/; Secure; HTTPOnly; SameSite=None", ACCESS_TOKEN, TOKEN_MAX_AGE_SHORT);
  String REFRESH_TOKEN_COOKIE_SHORT = format("folioRefreshToken=%s; Max-Age=%d; Expires=Thu, 10 A" +
      "ug 2023 19:44:44 GMT; Path=/authn; Secure; HTTPOnly; SameSite=None", REFRESH_TOKEN,
    TOKEN_MAX_AGE_SHORT);

  private final Map<String, String> headers = new HashMap<>();

  @Spy
  private final Vertx vertx = Vertx.vertx();
  @InjectMocks
  private final MessagingModuleDao messagingModuleDao = new MessagingModuleDaoImpl();
  private final Cache cache = new Cache(vertx, messagingModuleDao);
  private final SystemUserConfig systemUserConfig = new SystemUserConfig(SYSTEM_USER_NAME,
    SYSTEM_USER_PASSWORD, true);
  @Spy
  private final SecurityManagerImpl securityManager = new SecurityManagerImpl(cache, systemUserConfig);
  @Spy
  private final SecurityManagerImpl securityManagerNoSystemUser =
    new SecurityManagerImpl(cache, new SystemUserConfig(SYSTEM_USER_NAME,
      SYSTEM_USER_PASSWORD, false));

  private final Context vertxContext = vertx.getOrCreateContext();

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
    headers.put(OKAPI_TOKEN_HEADER, ACCESS_TOKEN);
  }

  @Test
  public void shouldLoginPubSubUser(TestContext context) {
    Async async = context.async();

    stubFor(post(LOGIN_URL)
      .willReturn(created()
        .withHeader("Set-Cookie", ACCESS_TOKEN_COOKIE)
        .withHeader("Set-Cookie", REFRESH_TOKEN_COOKIE)
      ));

    OkapiConnectionParams params = new OkapiConnectionParams();
    params.setVertx(vertx);
    params.setOkapiUrl(headers.get(OKAPI_URL_HEADER));
    params.setTenantId(TENANT);

    Future<String> future = securityManager.getAccessToken(params);

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(ACCESS_TOKEN, ar.result());
      List<LoggedRequest> requests = findAll(RequestPatternBuilder.allRequests());
      context.assertEquals(1, requests.size());
      context.assertEquals(LOGIN_URL, requests.get(0).getUrl());
      context.assertEquals("POST", requests.get(0).getMethod().getName());
      String actualToken = cache.getAccessToken(params.getTenantId());
      context.assertEquals(ACCESS_TOKEN, actualToken);
      async.complete();
    });
  }

  @Test
  public void shouldNotCreatePubSubUserIfItExists(TestContext context) {
    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUpToDateUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY).willReturn(ok().withBody(userCollection)));

    String permId = UUID.randomUUID().toString();
    JsonObject permUser = new JsonObject()
      .put("id", permId)
      .put("userId", userId)
      .put("permissions", new JsonArray());

    stubFor(get(PERMISSIONS_URL + "/" + userId + "?indexField=userId").willReturn(ok().withBody(permUser.encode())));
    stubFor(put(PERMISSIONS_URL + "/" + permId).willReturn(ok()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Void> future = securityManager.createPubSubUser(params);

    future.map(ar -> {
      List<LoggedRequest> requests = findAll(RequestPatternBuilder.allRequests());
      assertEquals(3, requests.size());

      assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      assertEquals("GET", requests.get(0).getMethod().getName());

      assertEquals(PERMISSIONS_URL + "/" + userId + "?indexField=userId", requests.get(1).getUrl());
      assertEquals("GET", requests.get(1).getMethod().getName());

      assertEquals(PERMISSIONS_URL + "/" + permId, requests.get(2).getUrl());
      assertEquals("PUT", requests.get(2).getMethod().getName());

      // Verify user create request has not sent
      verify(0, new RequestPatternBuilder(POST, urlEqualTo(USERS_URL)));

      return null;
    }).onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void shouldNotCreatePubSubUserIfEnvVariable(TestContext context) {
    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUpToDateUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY).willReturn(ok().withBody(userCollection)));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Void> future = securityManagerNoSystemUser.createPubSubUser(params);

    future.map(ar -> {
      List<LoggedRequest> requests = findAll(RequestPatternBuilder.allRequests());
      assertEquals(0, requests.size());

      // Verify user create request has not sent
      verify(0, new RequestPatternBuilder(POST, urlEqualTo(USERS_URL)));

      return null;
    }).onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void shouldNotCreatePermUserAndSamePermissions(TestContext context) {
    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUpToDateUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY).willReturn(ok().withBody(userCollection)));

    String permId = UUID.randomUUID().toString();
    JsonObject permUser = new JsonObject()
      .put("id", permId)
      .put("userId", userId)
      .put("permissions", new JsonArray().add("inventory.all"));

    stubFor(get(PERMISSIONS_URL + "/" + userId + "?indexField=userId").willReturn(ok().withBody(permUser.encode())));
    stubFor(put(PERMISSIONS_URL + "/" + permId).willReturn(ok()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Void> future = securityManager.createPubSubUser(params);

    future.map(ar -> {
      List<LoggedRequest> requests = findAll(RequestPatternBuilder.allRequests());
      assertEquals(2, requests.size());

      assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      assertEquals("GET", requests.get(0).getMethod().getName());

      assertEquals(PERMISSIONS_URL + "/" + userId + "?indexField=userId", requests.get(1).getUrl());
      assertEquals("GET", requests.get(1).getMethod().getName());

      // Verify user create request has not sent
      verify(0, new RequestPatternBuilder(POST, urlEqualTo(USERS_URL)));

      return null;
    }).onComplete(context.asyncAssertSuccess());
  }


  @Test
  public void shouldCreatePubSubUser(TestContext context) {
    String userId = UUID.randomUUID().toString();
    System.out.println("Userid=" + userId);
    String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY)
      .willReturn(ok().withBody(emptyUsersResponse().encode())));
    stubFor(post(USERS_URL).willReturn(created().withBody(userCollection)));
    stubFor(post(CREDENTIALS_URL).willReturn(created()));
    stubFor(post(PERMISSIONS_URL).willReturn(created()));
    stubFor(get(urlPathMatching(PERMISSIONS_URL + "/.*")).willReturn(notFound()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Void> future = securityManager.createPubSubUser(params);

    future.map(ar -> {
      List<LoggedRequest> requests = findAll(RequestPatternBuilder.allRequests());
      assertEquals(5, requests.size());

      assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      assertEquals("GET", requests.get(0).getMethod().getName());

      assertEquals(USERS_URL, requests.get(1).getUrl());
      assertEquals("POST", requests.get(1).getMethod().getName());
      verifyUser(requests.get(1));

      assertEquals(CREDENTIALS_URL, requests.get(2).getUrl());
      assertEquals("POST", requests.get(2).getMethod().getName());

      assertTrue( requests.get(4).getUrl().startsWith(PERMISSIONS_URL));
      assertEquals("GET", requests.get(3).getMethod().getName());

      assertTrue( requests.get(4).getUrl().startsWith(PERMISSIONS_URL));
      assertEquals("POST", requests.get(4).getMethod().getName());

      return null;
    }).onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void shouldLoginPubSubUserWhenContextHasNoToken(TestContext context) {
    Async async = context.async();

    stubFor(post(LOGIN_URL)
      .willReturn(created()
        .withHeader("Set-Cookie", ACCESS_TOKEN_COOKIE)
        .withHeader("Set-Cookie", REFRESH_TOKEN_COOKIE)
      ));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<String> future = securityManager.getAccessToken(params);

    future.onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(ACCESS_TOKEN, ar.result());
      verify(1, postRequestedFor(urlEqualTo(LOGIN_URL)));
      async.complete();
    });
  }

  @Test
  public void shoulReturnFailedFutureWhenTokenCacheIsEmptyAndPubSubUserLoginFailed(TestContext context) {
    Async async = context.async();
    stubFor(post(LOGIN_URL).willReturn(serverError()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<String> future = securityManager.getAccessToken(params);

    future.onComplete(ar -> {
      context.assertTrue(ar.failed());
      verify(1, postRequestedFor(urlEqualTo(LOGIN_URL)));
      async.complete();
    });
  }

  @Test
  public void shouldUpdateExistingUser(TestContext context) {
    final String userId = UUID.randomUUID().toString();
    final String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY).willReturn(ok().withBody(userCollection)));
    stubFor(put(USERS_URL + "/" + userId).willReturn(noContent()));

    String permId = UUID.randomUUID().toString();
    JsonObject permUser = new JsonObject()
      .put("id", permId)
      .put("userId", userId)
      .put("permissions", new JsonArray());

    stubFor(get(PERMISSIONS_URL + "/" + userId + "?indexField=userId").willReturn(ok().withBody(permUser.encode())));
    stubFor(put(PERMISSIONS_URL + "/" + permId).willReturn(ok()));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<Void> future = securityManager.createPubSubUser(params);

    future.map(ar -> {
      final List<LoggedRequest> requests = findAll(RequestPatternBuilder.allRequests());
      assertEquals(4, requests.size());

      assertEquals(USERS_URL_WITH_QUERY, requests.get(0).getUrl());
      assertEquals("GET", requests.get(0).getMethod().getName());

      assertEquals(USERS_URL + "/" + userId, requests.get(1).getUrl());
      assertEquals("PUT", requests.get(1).getMethod().getName());
      verifyUser(requests.get(1));

      assertEquals(PERMISSIONS_URL + "/" + userId + "?indexField=userId", requests.get(2).getUrl());
      assertEquals("GET", requests.get(2).getMethod().getName());

      assertEquals(PERMISSIONS_URL + "/" + permId, requests.get(3).getUrl());
      assertEquals("PUT", requests.get(3).getMethod().getName());

      return null;
    }).onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void permissionsFailGet(TestContext context) {
    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY)
      .willReturn(ok().withBody(emptyUsersResponse().encode())));
    stubFor(post(USERS_URL).willReturn(created().withBody(userCollection)));
    stubFor(post(CREDENTIALS_URL).willReturn(created()));
    stubFor(get(urlPathMatching(PERMISSIONS_URL + "/.*")).willReturn(forbidden().withBody("x")));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    securityManager.createPubSubUser(params).onComplete(context.asyncAssertFailure(res -> {
       assertEquals("Failed to get permissions for test-pubsub-username user. Received status code 403: x", res.getMessage());
    }));
  }

  @Test
  public void permissionsFailPost(TestContext context) {
    String userId = UUID.randomUUID().toString();
    String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY)
      .willReturn(ok().withBody(emptyUsersResponse().encode())));
    stubFor(post(USERS_URL).willReturn(created().withBody(userCollection)));
    stubFor(post(CREDENTIALS_URL).willReturn(created()));
    stubFor(get(urlPathMatching(PERMISSIONS_URL + "/.*")).willReturn(notFound()));
    stubFor(post(PERMISSIONS_URL).willReturn(forbidden().withBody("x")));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    securityManager.createPubSubUser(params).onComplete(context.asyncAssertFailure(res -> {
      assertEquals("Failed to add permissions inventory.all for test-pubsub-username user."
        + " Received status code 403: x", res.getMessage());
    }));
  }

  @Test
  public void permissionsFailPut(TestContext context) {
    final String userId = UUID.randomUUID().toString();
    final String userCollection = new JsonObject()
      .put("users", new JsonArray().add(existingUser(userId)))
      .put("totalRecords", 1).encode();

    stubFor(get(USERS_URL_WITH_QUERY).willReturn(ok().withBody(userCollection)));
    stubFor(put(USERS_URL + "/" + userId).willReturn(noContent()));

    String permId = UUID.randomUUID().toString();
    JsonObject permUser = new JsonObject()
      .put("id", permId)
      .put("userId", userId)
      .put("permissions", new JsonArray());

    stubFor(get(PERMISSIONS_URL + "/" + userId + "?indexField=userId").willReturn(ok().withBody(permUser.encode())));
    stubFor(put(PERMISSIONS_URL + "/" + permId).willReturn(forbidden().withBody("x")));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    securityManager.createPubSubUser(params).onComplete(context.asyncAssertFailure(res -> {
      assertEquals("Failed to update permissions inventory.all for test-pubsub-username user."
        + " Received status code 403: x", res.getMessage());
    }));
  }

  @Test(expected = NoSuchElementException.class)
  public void shouldFailReadingPermissionsOnEmptyPermissionsFile() {
    SecurityManagerImpl.readPermissionsFromResource("permissions/emptyFile");
  }

  @Test
  public void checkThatInvalidateTokenRemovesTokenForTenant() {
    cache.setAccessToken(TENANT, new ExpiryAwareToken(ACCESS_TOKEN, TOKEN_MAX_AGE, null));
    assertEquals(ACCESS_TOKEN, cache.getAccessToken(TENANT));
    cache.invalidateAccessToken(TENANT);
    assertNull(cache.getAccessToken(TENANT));

    cache.setRefreshToken(TENANT, new ExpiryAwareToken(REFRESH_TOKEN, TOKEN_MAX_AGE, null));
    assertEquals(REFRESH_TOKEN, cache.getRefreshToken(TENANT));
    cache.invalidateRefreshToken(TENANT);
    assertNull(cache.getRefreshToken(TENANT));
  }

  @Test
  public void checkCacheRefreshesAfterHalfOfMaxAge(TestContext context) {
    Async async = context.async();

    stubFor(post(LOGIN_URL)
      .willReturn(created()
        .withHeader("Set-Cookie", ACCESS_TOKEN_COOKIE_SHORT)
        .withHeader("Set-Cookie", REFRESH_TOKEN_COOKIE_SHORT)
      ));

    OkapiConnectionParams params = new OkapiConnectionParams();
    params.setVertx(vertx);
    params.setOkapiUrl(headers.get(OKAPI_URL_HEADER));
    params.setTenantId(TENANT);

    Future<String> future = securityManager.getAccessToken(params);

    future.onComplete(ar -> {
      context.assertEquals(TOKEN_MAX_AGE_SHORT, cache.getAccessExpiryAwareToken(TENANT).getMaxAge());
      var accessToken = cache.getAccessExpiryAwareToken(TENANT);
      // Comparing links to make sure it expires
      Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .until(() -> cache.getAccessExpiryAwareToken(TENANT) != accessToken);
      async.complete();
    });
  }

  private void verifyUser(LoggedRequest loggedRequest) {
    final User user = decodeValue(loggedRequest.getBodyAsString(), User.class);

    assertNotNull(user.getId());
    assertTrue(user.isActive());
    assertEquals(SYSTEM_USER_NAME, user.getUsername());

    assertNotNull(user.getPersonal());
    assertEquals("System", user.getPersonal().getLastName());
  }

  private JsonObject existingUser(String id) {
    final JsonObject metadata = new JsonObject()
      .put("createdDate", Instant.now())
      .put("updatedDate", Instant.now());

    return new JsonObject()
      .put("id", id)
      .put("username", SYSTEM_USER_NAME)
      .put("active", "true")
      .put("type", SYSTEM_USER_TYPE)
      .put("proxyFor", new JsonArray())
      .put("createdDate", Instant.now())
      .put("updatedDate", Instant.now())
      .put("metadata", metadata);
  }

  private JsonObject existingUpToDateUser(String id) {
    final JsonObject personal = new JsonObject()
      .put("lastName", "System")
      .put("addresses", new JsonArray());

    return existingUser(id).put("type", SYSTEM_USER_TYPE).put("personal", personal);
  }

  private JsonObject emptyUsersResponse() {
    return new JsonObject().put("users", new JsonArray());
  }
}
