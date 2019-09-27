package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.model.SubscriptionDefinition;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(VertxUnitRunner.class)
public class SubscribersApiTest extends AbstractRestTest {

  private EventDescriptor eventDescriptor = new EventDescriptor()
    .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA")
    .withDescription("Created SRS Marc Bibliographic Record with order data in 9xx fields")
    .withEventTTL(1)
    .withSigned(false);

  private EventDescriptor eventDescriptor2 = new EventDescriptor()
    .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_INVOICE_DATA")
    .withDescription("Created SRS Marc Bibliographic Record with incoice data in 9xx fields")
    .withEventTTL(1)
    .withSigned(false);

  @Test
  public void shouldReturnEmptyListOnGet() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + eventDescriptor.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
  }

  @Test
  public void shouldReturnSubscribersOnGetByEventType() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    EventDescriptor createdEventDescriptor2 = postEventDescriptor(eventDescriptor2);

    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor1.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    SubscriberDescriptor subscriberDescriptor2 = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(new SubscriptionDefinition()
          .withEventType(createdEventDescriptor2.getEventType())
          .withCallbackAddress("")))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor2)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1))
      .body("messagingModules.get(0).eventType", is(createdEventDescriptor1.getEventType()));
  }

  @Test
  public void shouldCreateSubscriberOnPost() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);

    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor1.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + SUBSCRIBERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  @Test
  public void shouldReturnBadRequestOnPostWhenAnyEventTypeIsNotExists() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);

    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor1.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriptionDefinition subscriptionDefinition2 = new SubscriptionDefinition()
      .withEventType(eventDescriptor2.getEventType())
      .withCallbackAddress("/test-callback");

    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Arrays.asList(subscriptionDefinition, subscriptionDefinition2))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body("total_records", is(1))
      .body("errors.get(0).message", notNullValue(String.class));
  }

  @Test
  public void shouldDeleteSubscriberOnDelete() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .queryParam("moduleName", subscriberDescriptor.getModuleName())
      .when()
      .delete(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
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
}
