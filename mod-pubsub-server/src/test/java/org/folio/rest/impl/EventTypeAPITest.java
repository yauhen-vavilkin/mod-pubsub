package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class EventTypeAPITest extends AbstractRestTest {

  private EventDescriptor eventDescriptor = new EventDescriptor()
    .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA")
    .withDescription("Created SRS Marc Bibliographic Record with order data in 9xx fields")
    .withEventTTL(1)
    .withSigned(false);

  @Test
  public void shouldReturnEmptyListOnGet() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0))
      .body("eventDescriptors", empty());
  }

  @Test
  public void shouldReturnAllEventDescriptorsOnGet() {
    postEventDescriptor(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  @Test
  public void shouldReturnNotFoundOnGetByEventType() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + eventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnEventDescriptorOnGetByEventType() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("eventType", is(eventDescriptor.getEventType()))
      .body("description", is(eventDescriptor.getDescription()))
      .body("eventTTL", is(eventDescriptor.getEventTTL()))
      .body("signed", is(eventDescriptor.getSigned()));
  }

  @Test
  public void shouldCreateEventDescriptorOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(eventDescriptor)
      .when()
      .post(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  @Test
  public void shouldReturnBadRequestOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .post(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnOkOnPostIfEventTypeWithTheSameDescriptorExists() {
    postEventDescriptor(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(eventDescriptor)
      .when()
      .post(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  @Test
  public void shouldReturnBadRequestOnPostIfEventTypeWithDifferentDescriptorExists() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(new EventDescriptor()
        .withEventType(createdEventDescriptor.getEventType())
        .withDescription("Test description")
        .withEventTTL(1)
        .withSigned(false))
      .when()
      .post(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldUpdateEventDescriptorOnPut() {
    EventDescriptor eventDescriptor = new EventDescriptor()
      .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA")
      .withDescription("Created SRS Marc Bibliographic Record with order data in 9xx fields")
      .withEventTTL(1)
      .withSigned(false);

    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);
    createdEventDescriptor.setDescription("New description");

    RestAssured.given()
      .spec(spec)
      .body(createdEventDescriptor)
      .when()
      .put(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("eventType", is(createdEventDescriptor.getEventType()))
      .body("description", is(createdEventDescriptor.getDescription()))
      .body("eventTTL", is(createdEventDescriptor.getEventTTL()))
      .body("signed", is(createdEventDescriptor.getSigned()));
  }

  @Test
  public void shouldReturnBadRequestOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .put(EVENT_TYPES_PATH + "/" + eventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnNotFoundOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(eventDescriptor)
      .when()
      .put(EVENT_TYPES_PATH + "/" + eventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldDeleteEventDescriptorOnDelete() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBadRequestOnDeleteIfModulesAreRegistered() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor))
      .withModuleId("mod-very-important-1.0.0");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnNotFoundOnDelete() {
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(EVENT_TYPES_PATH + "/" + "NONEXISTENT_EVENT_TYPE")
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldOverwriteTmpDescriptor() {
    EventDescriptor tmpEventDescriptor = new EventDescriptor()
      .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA")
      .withEventTTL(1)
      .withTmp(true);

    postEventDescriptor(tmpEventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(eventDescriptor)
      .when()
      .post(EVENT_TYPES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
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
