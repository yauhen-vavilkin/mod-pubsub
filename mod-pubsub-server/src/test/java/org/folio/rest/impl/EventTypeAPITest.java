package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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
    EventDescriptor createdEventDescriptor = postEventDescriptor(this.eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("eventType", is(this.eventDescriptor.getEventType()))
      .body("description", is(this.eventDescriptor.getDescription()))
      .body("eventTTL", is(this.eventDescriptor.getEventTTL()))
      .body("signed", is(this.eventDescriptor.getSigned()));
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
    postEventDescriptor(this.eventDescriptor);

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
    EventDescriptor createdEventDescriptor = postEventDescriptor(this.eventDescriptor);

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
  public void shouldReturnNotFoundOnDelete() {
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(EVENT_TYPES_PATH + "/" + "NONEXISTENT_EVENT_TYPE")
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
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
