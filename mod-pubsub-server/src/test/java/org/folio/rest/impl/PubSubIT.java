package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Test that the shaded fat uber jar works and that the Dockerfile works.
 */
public class PubSubIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(PubSubIT.class);

  private static final Network network = Network.newNetwork();

  @ClassRule
  public static final GenericContainer<?> module =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-pubsub").withFileFromPath("..", Path.of("..")))
    .withNetwork(network)
    .withExposedPorts(8081)
    .withEnv("DB_HOST", "postgres")
    .withEnv("DB_PORT", "5432")
    .withEnv("DB_USERNAME", "username")
    .withEnv("DB_PASSWORD", "password")
    .withEnv("DB_DATABASE", "postgres");

  @ClassRule
  public static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:12-alpine")
    .withNetwork(network)
    .withNetworkAliases("postgres")
    .withExposedPorts(5432)
    .withUsername("username")
    .withPassword("password")
    .withDatabaseName("postgres");

  @BeforeClass
  public static void beforeClass() {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + module.getHost() + ":" + module.getFirstMappedPort();
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeader("X-Okapi-Tenant", "testtenant")
        .setContentType(ContentType.JSON)
        .build();

    module.followOutput(new Slf4jLogConsumer(LOGGER).withSeparateOutputStreams());
  }

  @Test
  public void health() {
    when().
      get("/admin/health").
    then().
      statusCode(200).
      body(is("\"OK\""));
  }

  private void postTenant(JsonObject body) {
    String location =
        given().
          body(body.encodePrettily()).
        when().
          post("/_/tenant").
        then().
          statusCode(201).
        extract().
          header("Location");

    when().
      get(location + "?wait=30000").
    then().
      statusCode(200).  // getting job record succeeds
      body("complete", is(true)).  // job is complete
      body("error", is(nullValue()));  // job has succeeded without error
  }

  @Test
  public void installAndUpgrade() {
    postTenant(new JsonObject().put("module_to", "999999.0.0"));
    // migrate from 0.0.0 to test that migration is idempotent
    postTenant(new JsonObject().put("module_to", "999999.0.0").put("module_from", "0.0.0"));

    // smoke test
    given().
      body(new JsonObject()
          .put("eventType", "FOLIO_RELEASE_PARTY_ANNOUNCEMENT")
          .put("eventTTL", "1")
          .encodePrettily()).
    when().
      post("/pubsub/event-types").
    then().
      statusCode(201).
      body("eventType", is("FOLIO_RELEASE_PARTY_ANNOUNCEMENT"));
  }

}
