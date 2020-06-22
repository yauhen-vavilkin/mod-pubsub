package org.folio.services.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import org.folio.kafka.KafkaConfig;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.services.SecurityManager;
import org.folio.services.cache.Cache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class ConsumerServiceUnitTest {

  private static final String TENANT = "diku";
  private static final String TOKEN = "token";
  private static final String CALLBACK_ADDRESS = "/source-storage/records";
  private static final String EVENT_TYPE = "record_created";

  private Vertx vertx = Vertx.vertx();
  @Mock
  private KafkaConfig kafkaConfig;
  @Mock
  private Cache cache;
  @Mock
  private SecurityManager securityManager;
  @Spy
  @InjectMocks
  private KafkaConsumerServiceImpl consumerService = new KafkaConsumerServiceImpl(vertx, kafkaConfig, securityManager, cache);

  private Map<String, String> headers = new HashMap<>();

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(securityManager.getJWTToken(any(OkapiConnectionParams.class))).thenReturn(Future.succeededFuture(TOKEN));
    when(securityManager.loginPubSubUser(any(OkapiConnectionParams.class))).thenReturn(Future.succeededFuture(true));

    headers.put(OKAPI_URL_HEADER, "http://localhost:" + mockServer.port());
    headers.put(OKAPI_TENANT_HEADER, TENANT);
    headers.put(OKAPI_TOKEN_HEADER, TOKEN);
  }

  @Test
  public void shouldSendRequestWithoutPayloadToSubscriber(TestContext context) {
    Async async = context.async();

    WireMock.stubFor(WireMock.post(CALLBACK_ADDRESS)
      .willReturn(WireMock.ok()));

    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withEventMetadata(new EventMetadata()
        .withTenantId(TENANT)
        .withEventTTL(30)
        .withPublishedBy("mod-very-important-1.0.0"));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<HttpResponse<Buffer>> future = RestUtil.doRequest(event.getEventPayload(), CALLBACK_ADDRESS, HttpMethod.POST, params);

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      Assert.assertEquals(1, requests.size());
      Assert.assertEquals(CALLBACK_ADDRESS, requests.get(0).getUrl());
      Assert.assertEquals("POST", requests.get(0).getMethod().getName());
      async.complete();
    });
  }

  @Test
  public void shouldSendRequestWithPayloadToSubscriber(TestContext context) {
    Async async = context.async();

    WireMock.stubFor(WireMock.post(CALLBACK_ADDRESS)
      .willReturn(WireMock.created()));

    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withEventMetadata(new EventMetadata()
        .withTenantId(TENANT)
        .withEventTTL(30)
        .withPublishedBy("mod-very-important-1.0.0"))
      .withEventPayload("Very important");

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    Future<HttpResponse<Buffer>> future = RestUtil.doRequest(event.getEventPayload(), CALLBACK_ADDRESS, HttpMethod.POST, params);

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      Assert.assertEquals(1, requests.size());
      Assert.assertEquals(CALLBACK_ADDRESS, requests.get(0).getUrl());
      Assert.assertEquals("POST", requests.get(0).getMethod().getName());
      Assert.assertEquals(event.getEventPayload(), requests.get(0).getBodyAsString());
      async.complete();
    });
  }

  @Test
  public void shouldNotSendRequestIfNoSubscribersFound(TestContext context) {
    Async async = context.async();

    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withEventMetadata(new EventMetadata()
        .withTenantId(TENANT)
        .withEventTTL(30)
        .withPublishedBy("mod-very-important-1.0.0"));

    OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);

    when(cache.getMessagingModules()).thenReturn(Future.succeededFuture(new HashSet<>()));

    Future<Void> future = consumerService.deliverEvent(event, params);

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      List<LoggedRequest> requests = WireMock.findAll(RequestPatternBuilder.allRequests());
      Assert.assertEquals(0, requests.size());
      async.complete();
    });
  }

  @Test
  public void shouldSendRequestToFoundSubscribers(TestContext context) {
    Async async = context.async();

    WireMock.stubFor(WireMock.post(CALLBACK_ADDRESS)
      .willReturn(WireMock.noContent()));

    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withEventMetadata(new EventMetadata()
        .withTenantId(TENANT)
        .withEventTTL(30)
        .withPublishedBy("mod-very-important-1.0.0"));

    OkapiConnectionParams params = new OkapiConnectionParams(vertx);
    params.setHeaders(headers);
    params.setOkapiUrl(headers.getOrDefault("x-okapi-url", "localhost"));
    params.setTenantId(headers.getOrDefault("x-okapi-tenant", TENANT));
    params.setToken(headers.getOrDefault("x-okapi-token", TOKEN));
    params.setTimeout(2000);

    Set<MessagingModule> messagingModuleList = new HashSet<>();
    messagingModuleList.add(new MessagingModule()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withModuleId("mod-source-record-storage-1.0.0")
      .withTenantId(TENANT)
      .withModuleRole(MessagingModule.ModuleRole.SUBSCRIBER)
      .withActivated(true)
      .withSubscriberCallback(CALLBACK_ADDRESS));
    messagingModuleList.add(new MessagingModule()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withModuleId("mod-source-record-manager-1.0.0")
      .withTenantId(TENANT)
      .withModuleRole(MessagingModule.ModuleRole.SUBSCRIBER)
      .withActivated(true)
      .withSubscriberCallback(CALLBACK_ADDRESS));
    when(cache.getMessagingModules()).thenReturn(Future.succeededFuture(messagingModuleList));

    Future<Void> future = consumerService.deliverEvent(event, params);

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      verify(consumerService, times(messagingModuleList.size())).getEventDeliveredHandler(any(Event.class), anyString(), any(MessagingModule.class), any(OkapiConnectionParams.class), any(Map.class));
      async.complete();
    });
  }

  @Test
  public void shouldSendRequestAndRetry(TestContext context) {
    Async async = context.async();

    WireMock.stubFor(WireMock.post(CALLBACK_ADDRESS)
      .willReturn(WireMock.forbidden()));

    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withEventMetadata(new EventMetadata()
        .withTenantId(TENANT)
        .withEventTTL(30)
        .withPublishedBy("mod-very-important-1.0.0"));

    OkapiConnectionParams params = new OkapiConnectionParams(vertx);
    params.setHeaders(headers);
    params.setOkapiUrl(headers.getOrDefault("x-okapi-url", "localhost"));
    params.setTenantId(headers.getOrDefault("x-okapi-tenant", TENANT));
    params.setToken(headers.getOrDefault("x-okapi-token", TOKEN));
    params.setTimeout(2000);

    Set<MessagingModule> messagingModuleList = new HashSet<>();
    messagingModuleList.add(new MessagingModule()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withModuleId("mod-source-record-storage-1.0.0")
      .withTenantId(TENANT)
      .withModuleRole(MessagingModule.ModuleRole.SUBSCRIBER)
      .withActivated(true)
      .withSubscriberCallback(CALLBACK_ADDRESS));
    messagingModuleList.add(new MessagingModule()
      .withId(UUID.randomUUID().toString())
      .withEventType(EVENT_TYPE)
      .withModuleId("mod-source-record-manager-1.0.0")
      .withTenantId(TENANT)
      .withModuleRole(MessagingModule.ModuleRole.SUBSCRIBER)
      .withActivated(true)
      .withSubscriberCallback(CALLBACK_ADDRESS));
    when(cache.getMessagingModules()).thenReturn(Future.succeededFuture(messagingModuleList));

    Future<Void> future = consumerService.deliverEvent(event, params);

    future.onComplete(ar -> {
      assertTrue(ar.succeeded());
      async.complete();
    });
  }

}
