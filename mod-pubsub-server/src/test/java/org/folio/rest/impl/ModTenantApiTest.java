package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.folio.representation.User;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.restassured.RestAssured;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ModTenantApiTest extends AbstractRestTest {
  private static final String MODULE_TO_VERSION = "mod-pubsub-1.0.0";
  private static final String TENANT_URL = "/_/tenant";
  private static final String USERS_URL = "/users";
  private static final String GET_PUBSUB_USER_URL = USERS_URL + "?query=username=pub-sub";

  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(
    new WireMockConfiguration().dynamicPort());

  @BeforeClass
  public static void setUpProxying() {
    // forward to okapi by default
    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom(OKAPI_URL))
      .atPriority(Integer.MAX_VALUE));
  }

  @Test
  public void shouldForwardUserUpdateError() {
    final String expectedErrorMessage = "User is broken";

    final User user = existingUser();
    final JsonObject userCollection = new JsonObject()
      .put("users", new JsonArray().add(JsonObject.mapFrom(user)));

    wireMockRule.stubFor(get(GET_PUBSUB_USER_URL)
      .willReturn(okJson(userCollection.toString())));
    wireMockRule.stubFor(put(userByIdUrl(user.getId()))
      .willReturn(aResponse().withStatus(400).withBody(expectedErrorMessage)));

    String body = RestAssured.given()
      .spec(spec)
      .header(OKAPI_URL_HEADER, mockOkapiUrl())
      .body(JsonObject.mapFrom(new TenantAttributes().withModuleTo(MODULE_TO_VERSION)).encode())
      .when().post(TENANT_URL)
      .then().statusCode(201)
      .extract().body().asString();

    String id = new JsonObject(body).getString("id");
    body = RestAssured.given()
      .spec(spec)
      .header(OKAPI_URL_HEADER, mockOkapiUrl())
      .when().get(TENANT_URL + "/" + id + "?wait=60000")
      .then().statusCode(200)
      .extract().body().asString();
    assertTrue(body, new JsonObject(body).getBoolean("complete"));
    assertEquals("Unable to update the pub-sub user: " + expectedErrorMessage,
      new JsonObject(body).getString("error"));
  }

  private User existingUser() {
    final User user = new User();

    user.setId(UUID.randomUUID().toString());
    user.setActive(true);
    user.setUsername("pub-sub");

    return user;
  }

  private String userByIdUrl(String id) {
    return USERS_URL + "/" + id;
  }

  private String mockOkapiUrl() {
    return "http://localhost:" + wireMockRule.port();
  }
}
