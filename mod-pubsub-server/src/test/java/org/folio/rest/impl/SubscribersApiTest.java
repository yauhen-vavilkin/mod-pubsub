package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
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
    .withSigned(false)
    .withTmp(false);

  private EventDescriptor eventDescriptor2 = new EventDescriptor()
    .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_INVOICE_DATA")
    .withDescription("Created SRS Marc Bibliographic Record with incoice data in 9xx fields")
    .withEventTTL(1)
    .withSigned(false)
    .withTmp(false);

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
  public void shouldReturnSubscribersOnGetByEventType(TestContext context) {
    Async async = context.async();
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    EventDescriptor createdEventDescriptor2 = postEventDescriptor(eventDescriptor2);
    async.complete();

    async = context.async();
    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor1.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleId("test-module-14.0.0");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    SubscriberDescriptor subscriberDescriptor2 = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(new SubscriptionDefinition()
          .withEventType(createdEventDescriptor2.getEventType())
          .withCallbackAddress("/callback-path2")))
      .withModuleId("another-test-module-1.10.0");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor2)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1))
      .body("messagingModules.get(0).eventType", is(createdEventDescriptor1.getEventType()));
    async.complete();
  }

  @Test
  public void shouldCreateSubscriberOnPost(TestContext context) {
    Async async = context.async();
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    async.complete();

    async = context.async();
    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor1.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleId("test-module-1.0.0");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + SUBSCRIBERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
    async.complete();
  }

  @Test
  public void shouldClearPreviousSubscriberInfoOnPostWithSameModuleNameAndTenantId(TestContext context) {
    Async async = context.async();
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    EventDescriptor createdEventDescriptor2 = postEventDescriptor(eventDescriptor2);
    String moduleName = "test-module-14.23.1";
    async.complete();

    async = context.async();
    SubscriptionDefinition subscriptionDefinition1 = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor1.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor1 = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition1))
      .withModuleId(moduleName);

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor1)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    // post subscriber with same module name and tenant id
    SubscriptionDefinition subscriptionDefinition2 = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor2.getEventType())
      .withCallbackAddress("/callback-path2");
    SubscriberDescriptor subscriberDescriptor2 = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition2))
      .withModuleId(moduleName);

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor2)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + SUBSCRIBERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
    async.complete();

    async = context.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor2.getEventType() + SUBSCRIBERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
    async.complete();
  }

  @Test
  public void shouldRegisterSubscriberIfEventTypesAreNotCreated(TestContext context) {
    Async async = context.async();
    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(eventDescriptor.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriptionDefinition subscriptionDefinition2 = new SubscriptionDefinition()
      .withEventType(eventDescriptor2.getEventType())
      .withCallbackAddress("/test-callback");

    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Arrays.asList(subscriptionDefinition, subscriptionDefinition2))
      .withModuleId("test-module-1.0.0");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();
  }

  @Test
  public void shouldDeleteSubscriberOnDelete(TestContext context) {
    Async async = context.async();
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(createdEventDescriptor.getEventType())
      .withCallbackAddress("/callback-path");
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleId("test-module-1.0.0-SNAPSHOT");

    RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", subscriberDescriptor.getModuleId())
      .when()
      .delete(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
    async.complete();

    async = context.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
    async.complete();
  }

  @Test
  public void shouldNotFailWhenRegisteringEmptySubscribersList(TestContext context) {
    Async async = context.async();
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.emptyList())
      .withModuleId("mod-important-1.0.0");

    Response postResponse = RestAssured.given()
      .spec(spec)
      .body(subscriberDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH);
    Assert.assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));
    async.complete();
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
