package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(VertxUnitRunner.class)
public class PublishersApiTest extends AbstractRestTest {

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
      .get(EVENT_TYPES_PATH + "/" + eventDescriptor.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
  }

  @Test
  public void shouldReturnPublisherOnGetByEventType() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    EventDescriptor createdEventDescriptor2 = postEventDescriptor(eventDescriptor2);

    PublisherDescriptor publisherDescriptor1 = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor1))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor1)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    PublisherDescriptor publisherDescriptor2 = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor2))
      .withModuleName("test-module2");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor2)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1))
      .body("messagingModules.get(0).eventType", is(createdEventDescriptor1.getEventType()));
  }

  @Test
  public void shouldCreatePublisherOnPost() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    EventDescriptor createdEventDescriptor2 = postEventDescriptor(eventDescriptor2);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Arrays.asList(createdEventDescriptor1, createdEventDescriptor2))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  @Test
  public void shouldReturnBadRequestOnPostWhenEventTypeIsNotExists() {
    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(eventDescriptor))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body("total_records", is(1))
      .body("errors.get(0).message", notNullValue(String.class));
  }

  @Test
  public void shouldReturnBadRequestOnPostWhenAnyEventTypeIsNotExists() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Arrays.asList(createdEventDescriptor1, eventDescriptor2))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body("total_records", is(1))
      .body("errors.get(0).message", notNullValue(String.class));
  }

  @Test
  public void shouldReturnBadRequestOnPostWhenAnyEventDescriptorIsNotSameAsExistingDescriptor() {
    int newEventTTL = 10;
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);
    createdEventDescriptor.withEventTTL(newEventTTL);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body("total_records", is(1))
      .body("errors.get(0).message", notNullValue(String.class));
  }

  @Test
  public void shouldReturnBadRequestOnPostWhenAnyEventDescriptorIsNotSameAsExistingDescriptorAnyEventTypeIsNotExists() {
    int newEventTTL = 10;
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);
    createdEventDescriptor.withEventTTL(newEventTTL);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Arrays.asList(createdEventDescriptor, eventDescriptor2))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body("total_records", is(2))
      .body("errors.get(0).message", notNullValue(String.class))
      .body("errors.get(1).message", notNullValue(String.class));
  }

  @Test
  public void shouldDeletePublisherOnDelete() {
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor))
      .withModuleName("test-module");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .queryParam("moduleName", publisherDescriptor.getModuleName())
      .when()
      .delete(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
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
