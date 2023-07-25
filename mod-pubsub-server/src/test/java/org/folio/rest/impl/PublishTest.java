package org.folio.rest.impl;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.kafka.PubSubConfig;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.model.SubscriptionDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class PublishTest extends AbstractRestTest {
  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(
    new WireMockConfiguration().dynamicPort());
  private static final String PUBLISH_PATH = "/pubsub/publish";
  private static final String CALLBACK_ADDRESS = "/call-me-maybe";
  private static final String LOGIN_URL = "/authn/login";
  private static final String USERS_URL = "/users";
  private static final String GET_PUBSUB_USER_URL = USERS_URL + "?query=username=" + SYSTEM_USER_NAME;
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
  public void shouldPublishEventIfNoSubscribersRegistered() {
    EventDescriptor eventDescriptor = postEventDescriptor(EVENT_DESCRIPTOR);
    registerPublisher(eventDescriptor);

    RestAssured.given()
      .spec(spec)
      .body(EVENT.encode())
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
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
  public void shouldPublishEventWithPayload() throws InterruptedException {
    EventDescriptor eventDescriptor = postEventDescriptor(EVENT_DESCRIPTOR);
    registerPublisher(eventDescriptor);
    registerSubscriber(eventDescriptor);

    // wait for kafka subscription to settle down
    Thread.sleep(3000); //NOSONAR

    RestAssured.given()
      .spec(spec)
      .body(EVENT.put("eventPayload", "something very important").encode())
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
    await()
      .atMost(15, TimeUnit.SECONDS)
      .alias(methodName)
      .pollInterval(3, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        System.out.println("Asserting subscriber notification...");
        verify(postRequestedFor(urlEqualTo(CALLBACK_ADDRESS)));});
  }

  @Test
  public void shouldPublishEventWithPayloadAndTenantCollectionTopicsEnabled() throws InterruptedException {
    try {
      PubSubConfig.setTenantCollectionTopicsQualifier("ALL");
      shouldPublishEventWithPayload();
    } finally {
      PubSubConfig.setTenantCollectionTopicsQualifier(null);
    }
  }

  private void registerPublisher(EventDescriptor eventDescriptor) {
    PublisherDescriptor publisherDescriptor = new PublisherDescriptor()
      .withEventDescriptors(Collections.singletonList(eventDescriptor))
      .withModuleId("mod-very-important-1.0.0");

    RestAssured.given()
      .spec(spec)
      .body(JsonObject.mapFrom(publisherDescriptor).encode())
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_PUBLISHER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
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

  private void registerSubscriber(EventDescriptor eventDescriptor) {
    SubscriptionDefinition subscriptionDefinition = new SubscriptionDefinition()
      .withEventType(eventDescriptor.getEventType())
      .withCallbackAddress(CALLBACK_ADDRESS);
    SubscriberDescriptor subscriberDescriptor = new SubscriberDescriptor()
      .withSubscriptionDefinitions(Collections.singletonList(subscriptionDefinition))
      .withModuleId("mod-important-1.0.0");

    RestAssured.given()
      .spec(spec.header(OKAPI_URL_HEADER, mockOkapiUrl()))
      .body(JsonObject.mapFrom(subscriberDescriptor).encode())
      .when()
      .post(EVENT_TYPES_PATH + DECLARE_SUBSCRIBER_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  @Before
  public void setUp(){
    wireMockRule.stubFor(any(urlEqualTo(CALLBACK_ADDRESS)));
    wireMockRule.stubFor(post(urlEqualTo(GET_PUBSUB_USER_URL))
      .willReturn(aResponse()
        .withBody("{\n" +
          "    \"users\": [\n" +
          "        {\n" +
          "            \"username\": \"test-pubsub-username\",\n" +
          "            \"id\": \"5a05e962-0502-5f78-a1fb-c47ba902298b\",\n" +
          "            \"active\": true,\n" +
          "            \"patronGroup\": \"3684a786-6671-4268-8ed0-9db82ebca60b\"\n" +
          "        }\n" +
          "    ],\n" +
          "    \"totalRecords\": 1\n" +
          "}")));
    wireMockRule.stubFor(post(urlEqualTo(LOGIN_URL))
      .willReturn(aResponse()
        .withHeader(OKAPI_TOKEN_HEADER, "okapi_token")
        .withStatus(201)));
  }

  @After
  public void cleanUp() {
    RestAssured.given()
      .spec(spec)
      .queryParam("moduleId", "mod-very-important-1.0.0")
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

  private String mockOkapiUrl() {
    return "http://localhost:"+ wireMockRule.port();
  }
}
