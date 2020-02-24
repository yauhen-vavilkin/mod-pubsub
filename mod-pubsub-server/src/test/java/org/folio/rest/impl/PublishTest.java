package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.model.SubscriptionDefinition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class PublishTest extends AbstractRestTest {

  private static final String PUBLISH_PATH = "/pubsub/publish";
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

  @Test
  public void shouldReturnBadRequestIfPublisherIsNotRegistered() {
    RestAssured.given()
      .spec(spec)
      .body(EVENT.encode())
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestIfNoSubscribersRegistered() {
    EventDescriptor eventDescriptor = postEventDescriptor(EVENT_DESCRIPTOR);
    registerPublisher(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(EVENT.encode())
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldPublishEvent() {
    EventDescriptor eventDescriptor = postEventDescriptor(EVENT_DESCRIPTOR);
    registerPublisher(eventDescriptor);
    registerSubscriber(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(EVENT.encode())
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @Test
  public void shouldPublishEventWithPayload() {
    EventDescriptor eventDescriptor = postEventDescriptor(EVENT_DESCRIPTOR);
    registerPublisher(eventDescriptor);
    registerSubscriber(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(EVENT.put("eventPayload", "something very important").encode())
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
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

  private EventDescriptor postEventDescriptor(EventDescriptor eventDescriptor) {
    Response postResponse = RestAssured.given()
      .spec(spec)
      .body(eventDescriptor)
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

  @After
  public void cleanUp() {
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId","mod-very-important-1.0.0")
      .when()
      .delete(EVENT_TYPES_PATH + "/record_created" + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_NO_CONTENT);
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", "mod-important-1.0.0")
      .when()
      .delete(EVENT_TYPES_PATH + "/record_created" + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }
}
