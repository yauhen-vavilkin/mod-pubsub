package org.folio.services.impl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.MessagingModuleDao;
import org.folio.kafka.KafkaConfig;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventMetadata;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.rest.util.OkapiConnectionParams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public class ConsumerServiceUnitTest {

  private static final String TENANT = "diku";
  private static final String TOKEN = "token";
  private static final String OKAPI_TENANT_HEADER = "x-okapi-tenant";
  private static final String OKAPI_TOKEN_HEADER = "x-okapi-token";
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String CALLBACK_ADDRESS = "/source-storage/records";
  private static final String EVENT_TYPE = "record_created";

  @Mock
  private KafkaConfig kafkaConfig;
  @Mock
  private MessagingModuleDao messagingModuleDao;
  @Spy
  @InjectMocks
  private KafkaConsumerServiceImpl consumerService = new KafkaConsumerServiceImpl(Vertx.vertx(), kafkaConfig, messagingModuleDao);

  private Map<String, String> headers = new HashMap<>();

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

    OkapiConnectionParams params = new OkapiConnectionParams(headers);

    Future<HttpClientResponse> future = consumerService.doRequest(event, CALLBACK_ADDRESS, params);

    future.setHandler(ar -> {
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

    OkapiConnectionParams params = new OkapiConnectionParams(headers);

    Future<HttpClientResponse> future = consumerService.doRequest(event, CALLBACK_ADDRESS, params);

    future.setHandler(ar -> {
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

    OkapiConnectionParams params = new OkapiConnectionParams(headers);

    when(messagingModuleDao.get(any(MessagingModuleFilter.class))).thenReturn(Future.succeededFuture(new ArrayList<>()));

    Future<Void> future = consumerService.deliverEvent(event, params);

    future.setHandler(ar -> {
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

    OkapiConnectionParams params = new OkapiConnectionParams();
    params.setHeaders(headers);
    params.setOkapiUrl(headers.getOrDefault("x-okapi-url", "localhost"));
    params.setTenantId(headers.getOrDefault("x-okapi-tenant", TENANT));
    params.setToken(headers.getOrDefault("x-okapi-token", TOKEN));

    List<MessagingModule> messagingModuleList = new ArrayList<>();
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
    when(messagingModuleDao.get(any(MessagingModuleFilter.class))).thenReturn(Future.succeededFuture(messagingModuleList));

    Future<Void> future = consumerService.deliverEvent(event, params);

    future.setHandler(ar -> {
      assertTrue(ar.succeeded());
      verify(consumerService, times(messagingModuleList.size())).doRequest(event, CALLBACK_ADDRESS, params);
      async.complete();
    });
  }

}
