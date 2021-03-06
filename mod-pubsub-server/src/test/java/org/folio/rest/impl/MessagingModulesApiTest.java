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
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class MessagingModulesApiTest extends AbstractRestTest {

  public static final String MESSAGING_MODULES_PATH = "/pubsub/messaging-modules";

  private EventDescriptor eventDescriptor = new EventDescriptor()
    .withEventType("DI_SRS_MARC_BIB_RECORD_CREATED")
    .withDescription("Created SRS Marc Bibliographic")
    .withEventTTL(1)
    .withSigned(false);

  @Test
  public void shouldDeletePublisherOnDeleteWhenPublisherRoleIsSpecified() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    String moduleId = "test-module-1.0.0";
    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor))
      .withModuleId(moduleId);

    RestAssured.given()
      .spec(spec)
      .body(JsonObject.mapFrom(publisherDescriptor).encode())
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", moduleId)
      .queryParam("moduleRole", PUBLISHER.value())
      .when()
      .delete(MESSAGING_MODULES_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + PUBLISHERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
  }

  @Test
  public void shouldDeleteSubscriberOnDeleteWhenSubscriberRoleIsSpecified() {
    String moduleId = "test-module-1.0.0";
    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(eventDescriptor.getEventType())
      .withCallbackAddress("/callback-path");

    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Arrays.asList(subscriptionDefinition))
      .withModuleId(moduleId);

    RestAssured.given()
      .spec(spec)
      .body(JsonObject.mapFrom(subscriberDescriptor).encode())
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", moduleId)
      .queryParam("moduleRole", SUBSCRIBER.value())
      .when()
      .delete(MESSAGING_MODULES_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + subscriptionDefinition.getEventType() + SUBSCRIBERS_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
  }

  @Test
  public void shouldReturnBadRequestOnDeleteWhenModuleIdIsNotSet() {
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleRole", PUBLISHER.value())
      .when()
      .delete(MESSAGING_MODULES_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestOnDeleteWhenModuleRoleIsNotSet() {
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", "test-module-1.0.0")
      .when()
      .delete(MESSAGING_MODULES_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestOnDeleteWhenInvalidModuleRoleIsSpecified() {
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", "test-module-1.0.0")
      .queryParam("moduleRole", "invalid role")
      .when()
      .delete(MESSAGING_MODULES_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  private EventDescriptor postEventDescriptor(EventDescriptor eventDescriptor) {
    Response postResponse = RestAssured.given()
      .spec(spec)
      .body(JsonObject.mapFrom(eventDescriptor).encode())
      .when()
      .post(EVENT_TYPES_PATH);
    assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));
    return new JsonObject(postResponse.body().asString()).mapTo(EventDescriptor.class);
  }
}
