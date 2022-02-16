package org.folio.util.pubsub;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.util.OkapiConnectionParams;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class PubSubClientTest {
  private static final String TENANT_ID = "diku";
  private static final String TOKEN = "token";
  private static final int PORT = NetworkUtils.nextFreePort();

  private static final OkapiConnectionParams params = new OkapiConnectionParams(Vertx.vertx());
  private static final JsonObject EVENT = new JsonObject()
    .put("id", UUID.randomUUID().toString())
    .put("eventType", "record_created")
    .put("eventMetadata", new JsonObject()
      .put("tenantId", TENANT_ID)
      .put("eventTTL", 30)
      .put("publishedBy", "mod-very-important-1.0.0"));
  private static final String DECLARE_PUBLISHER_PATH = "/pubsub/event-types/declare/publisher";
  private static final String DECLARE_SUBSCRIBER_PATH = "/pubsub/event-types/declare/subscriber";
  private static final String EVENT_TYPES_PATH = "/pubsub/event-types";
  private static final String MESSAGING_MODULES_PATH = "/pubsub/messaging-modules";
  private static final String PUBLISH_EVENT_PATH = "/pubsub/publish";

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .port(PORT)
      .notifier(new ConsoleNotifier(true)));

  @Before
  public void prepareParams() {
    params.setOkapiUrl("http://localhost:" + PORT);
    params.setTenantId(TENANT_ID);
    params.setToken(TOKEN);
  }

  @Test
  public void registerModuleSuccessfully() throws Exception {
    stubPubSubServer(created(), created(), created());

    assertTrue(PubSubClientUtils.registerModule(params).get());
  }

  @Test
  public void shouldNotRegisterPublishers() {
    stubPubSubServer(badRequest(), created(), created());

    try {
      PubSubClientUtils.registerModule(params).get();
      fail();
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void shouldNotRegisterSubscribers() {
    stubPubSubServer(created(), badRequest(), created());

    try {
      PubSubClientUtils.registerModule(params).get();
      fail();
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  public void shouldPublishEvent() {
    WireMock.stubFor(post(PUBLISH_EVENT_PATH).willReturn(noContent()));
    try {
      Event event = EVENT.mapTo(Event.class);
      assertTrue(PubSubClientUtils.sendEventMessage(event, params).get());
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void shouldUnregisterModuleSuccessfully() throws Exception {
    WireMock.stubFor(delete(new UrlPathPattern(new RegexPattern(MESSAGING_MODULES_PATH + "?.*"), true))
      .willReturn(noContent()));
    assertTrue(PubSubClientUtils.unregisterModule(params).get());
  }

  @Test(expected = ExecutionException.class)
  public void shouldReturnFailedFutureWhenPubsubReturnsServerError() throws Exception {
    WireMock.stubFor(delete(new UrlPathPattern(new RegexPattern(MESSAGING_MODULES_PATH + "?.*"), true))
      .willReturn(serverError()));
    PubSubClientUtils.unregisterModule(params).get();
  }

  private void stubPubSubServer(ResponseDefinitionBuilder declarePublisherResponse,
    ResponseDefinitionBuilder declareSubscriberResponse,
    ResponseDefinitionBuilder eventTypesResponse) {

    WireMock.stubFor(post(DECLARE_PUBLISHER_PATH).willReturn(declarePublisherResponse));
    WireMock.stubFor(post(DECLARE_SUBSCRIBER_PATH).willReturn(declareSubscriberResponse));
    WireMock.stubFor(post(EVENT_TYPES_PATH).willReturn(eventTypesResponse));
  }
}
