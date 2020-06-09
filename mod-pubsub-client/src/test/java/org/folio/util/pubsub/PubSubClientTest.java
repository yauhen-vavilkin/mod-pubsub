package org.folio.util.pubsub;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.model.SubscriptionDefinition;
import org.folio.rest.util.OkapiConnectionParams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class PubSubClientTest extends AbstractRestTest {

  private static OkapiConnectionParams params = new OkapiConnectionParams();
  private static OkapiConnectionParams fakeParams = new OkapiConnectionParams();
  private static final EventDescriptor EVENT_DESCRIPTOR = new EventDescriptor()
    .withEventType("record_created")
    .withDescription("Created SRS Marc Bibliographic Record with order data in 9xx fields")
    .withEventTTL(1)
    .withSigned(false);
  private static final JsonObject EVENT = new JsonObject()
    .put("id", UUID.randomUUID().toString())
    .put("eventType", "record_created")
    .put("eventMetadata", new JsonObject()
      .put("tenantId", TENANT_ID)
      .put("eventTTL", 30)
      .put("publishedBy", "mod-very-important-1.0.0"));
  private static final String EVENT_TYPES_PATH = "/pubsub/event-types";
  private static final String DECLARE_PUBLISHER_PATH = "/declare/publisher";
  private static final String DECLARE_SUBSCRIBER_PATH = "/declare/subscriber";

  @Before
  public void prepareParams() {
    params.setToken(TOKEN);
    params.setOkapiUrl(okapiUrl);
    params.setTenantId(TENANT_ID);
    fakeParams.setOkapiUrl(okapiUrlStub);
    fakeParams.setTenantId(TENANT_ID);
    fakeParams.setToken(TOKEN);
  }

  @Test
  public void registerModuleSuccessfully() throws Exception {
    Assert.assertTrue(PubSubClientUtils.registerModule(params).get());
  }

  @Test
  public void shouldNotRegisterPublishers() {
    WireMock.stubFor(post("/pubsub/event-types/declare/publisher")
      .willReturn(badRequest()));
    WireMock.stubFor(post("/pubsub/event-types")
      .willReturn(created()));
    try {
      PubSubClientUtils.registerModule(fakeParams).get();
      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(true);
    }
  }

  @Test
  public void shouldNotRegisterSubscribers() {
    WireMock.stubFor(post("/pubsub/event-types/declare/publisher")
      .willReturn(created()));
    WireMock.stubFor(post("/pubsub/event-types")
      .willReturn(created()));
    WireMock.stubFor(post("/pubsub/event-types/declare/subscriber")
      .willReturn(badRequest()));
    try {
      PubSubClientUtils.registerModule(fakeParams).get();
      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(true);
    }
  }

  @Test
  public void shouldPublishEventIfNoSubscribersRegistered() {
    EventDescriptor eventDescriptor = postEventDescriptor();
    registerPublisher(eventDescriptor);
    try {
      Event event = EVENT.mapTo(Event.class);
      Assert.assertTrue(PubSubClientUtils.sendEventMessage(event, params).get());
    } catch (Exception e) {
      Assert.fail();
    }
  }

  @Test
  public void shouldPublishEvent() {
    EventDescriptor eventDescriptor = postEventDescriptor();
    registerPublisher(eventDescriptor);
    registerSubscriber(eventDescriptor);

    try {
      Event event = EVENT.mapTo(Event.class);
      Assert.assertTrue(PubSubClientUtils.sendEventMessage(event, params).get());
    } catch (Exception e) {
      Assert.fail();
    }
  }

  private void registerPublisher(EventDescriptor eventDescriptor) {
    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(eventDescriptor))
      .withModuleId("mod-very-important-1.0.0");

    Response postResponse = RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH);
    Assert.assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));
  }

  private EventDescriptor postEventDescriptor() {
    Response postResponse = RestAssured.given()
      .spec(spec)
      .body(PubSubClientTest.EVENT_DESCRIPTOR)
      .when()
      .post(EVENT_TYPES_PATH);
    Assert.assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));
    return postResponse.body().as(EventDescriptor.class);
  }

  private void registerSubscriber(EventDescriptor eventDescriptor) {
    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(eventDescriptor.getEventType())
      .withCallbackAddress("/call-me-maybe");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleId("mod-important-1.0.0");

    Response postResponse = RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH);
    Assert.assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));
  }

}
