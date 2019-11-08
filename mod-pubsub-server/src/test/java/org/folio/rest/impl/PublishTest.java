package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(VertxUnitRunner.class)
public class PublishTest extends AbstractRestTest {

  private static final String PUBLISH_PATH = "/pubsub/publish";
  private static final String EVENT = new JsonObject()
    .put("id", UUID.randomUUID().toString())
    .put("eventType", "record_created")
    .put("eventMetadata", new JsonObject()
      .put("tenantId", TENANT_ID)
      .put("eventTTL", 30)
      .put("publishedBy", "mod-very-important-1.0.0")).encode();

  @Test
  public void shouldPublishEvent() {
    RestAssured.given()
      .spec(spec)
      .body(EVENT)
      .when()
      .post(PUBLISH_PATH)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }
}
