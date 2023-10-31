package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
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
import org.testcontainers.Testcontainers;
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
  public static final WireMockClassRule okapi = new WireMockClassRule();

  @ClassRule
  public static final GenericContainer<?> module =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-pubsub").withDockerfile(Path.of("../Dockerfile")))
      .withNetwork(network)
      .withExposedPorts(8081)
      .withAccessToHost(true)
      .withEnv("DB_HOST", "postgres")
      .withEnv("DB_PORT", "5432")
      .withEnv("DB_USERNAME", "username")
      .withEnv("DB_PASSWORD", "password")
      .withEnv("DB_DATABASE", "postgres")
      .withEnv("SYSTEM_USER_NAME", "test_user")
      .withEnv("SYSTEM_USER_PASSWORD", "test_password");

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
    okapi.start();
    Testcontainers.exposeHostPorts(okapi.port());
    okapi.stubFor(get("/users?query=username=pub-sub").willReturn(okJson("{\"users\":[]}")));
    okapi.stubFor(post("/users").willReturn(created()));
    okapi.stubFor(post("/authn/credentials").willReturn(created()));
    okapi.stubFor(get(urlPathMatching("/perms/users/.*")).willReturn(notFound()));
    okapi.stubFor(post("/perms/users").willReturn(created()));

    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + module.getHost() + ":" + module.getFirstMappedPort();
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeader("X-Okapi-Tenant", "testtenant")
        .addHeader("X-Okapi-Url", "http://host.testcontainers.internal:" + okapi.port())
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
