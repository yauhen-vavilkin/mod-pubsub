package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class PubSubAPITest extends AbstractRestTest {

  public static final String PUB_SUB_PATH = "/pubsub";

  @Test
  public void shouldReturnOkOnGet() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(PUB_SUB_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK);
  }


}
