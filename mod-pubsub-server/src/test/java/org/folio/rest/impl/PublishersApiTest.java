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
      .withModuleId("test-module-1.0.0");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor1)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    PublisherDescriptor publisherDescriptor2 = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor2))
      .withModuleId("another-test-module-1.0.0");

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
      .withModuleId("test-module-1.0.0");

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
  public void shouldClearPreviousPublisherInfoOnPostWithSameModuleNameAndTenantId() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);
    EventDescriptor createdEventDescriptor2 = postEventDescriptor(eventDescriptor2);
    String moduleName = "test-module-1.0.0";

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor1))
      .withModuleId(moduleName);

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    // post publisher with same module name and tenant id
    PublisherDescriptor publisherDescriptor2 = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor2))
      .withModuleId(moduleName);

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor2)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor1.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));

    RestAssured.given()
      .spec(spec)
      .when()
      .get(EVENT_TYPES_PATH + "/" + createdEventDescriptor2.getEventType() + PUBLISHERS_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  @Test
  public void shouldReturnBadRequestOnPostWhenEventTypeDoesNotExist() {
    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(eventDescriptor))
      .withModuleId("test-module-1.0.0");

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
  public void shouldReturnBadRequestOnPostWhenSpecificEventTypeDoesNotExist() {
    EventDescriptor createdEventDescriptor1 = postEventDescriptor(eventDescriptor);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Arrays.asList(createdEventDescriptor1, eventDescriptor2))
      .withModuleId("test-module-1.0.0");

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
  public void shouldReturnBadRequestOnDeclarePublisherWithEventDescriptorDifferentFromExistingOne() {
    int newEventTTL = 10;
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);
    createdEventDescriptor.withEventTTL(newEventTTL);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(createdEventDescriptor))
      .withModuleId("test-module-1.0.0");

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
  public void shouldReturnBadRequestOnPostWhenEventDescriptorDoesNotExist() {
    int newEventTTL = 10;
    EventDescriptor createdEventDescriptor = postEventDescriptor(eventDescriptor);
    createdEventDescriptor.withEventTTL(newEventTTL);

    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Arrays.asList(createdEventDescriptor, eventDescriptor2))
      .withModuleId("test-module-1.0.0");

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
      .withModuleId("test-module-1.0.0");

    RestAssured.given()
      .spec(spec)
      .body(publisherDescriptor)
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", publisherDescriptor.getModuleId())
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

  @Test
  public void shouldNotFailWhenRegisteringEmptyPublishersList() {
    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.emptyList())
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
}
