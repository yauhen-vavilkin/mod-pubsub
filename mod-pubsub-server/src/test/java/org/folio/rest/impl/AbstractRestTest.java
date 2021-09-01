package org.folio.rest.impl;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.sqlclient.Tuple;
import net.mguenther.kafka.junit.EmbeddedKafkaCluster;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import static java.lang.String.format;
import static net.mguenther.kafka.junit.EmbeddedKafkaCluster.provisionWith;
import static net.mguenther.kafka.junit.EmbeddedKafkaClusterConfig.useDefaults;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

public abstract class AbstractRestTest {
  protected static final String TENANT_ID = "diku";
  private static final String TOKEN = "token";
  private static final String HTTP_PORT = "http.port";
  private static final String DELETE_ALL_SQL = "DELETE FROM pubsub_config.%s";
  private static final String EVENT_DESCRIPTOR_TABLE = "event_descriptor";
  private static final String MESSAGING_MODULE_TABLE = "messaging_module";
  private static final String AUDIT_MESSAGE_PAYLOAD_TABLE = "audit_message_payload";
  private static final String AUDIT_MESSAGE_TABLE = "audit_message";

  protected static final String EVENT_TYPES_PATH = "/pubsub/event-types";
  protected static final String DECLARE_PUBLISHER_PATH = "/declare/publisher";
  protected static final String PUBLISHERS_PATH = "/publishers";
  protected static final String DECLARE_SUBSCRIBER_PATH = "/declare/subscriber";
  protected static final String SUBSCRIBERS_PATH = "/subscribers";
  protected static final String HISTORY_PATH = "pubsub/history";
  protected static final String AUDIT_MESSAGES_PAYLOAD_PATH = "/pubsub/audit-messages/%s/payload";

  private static final String KAFKA_HOST = "KAFKA_HOST";
  private static final String KAFKA_PORT = "KAFKA_PORT";
  private static final String OKAPI_URL_ENV = "OKAPI_URL";

  private static final int PORT = NetworkUtils.nextFreePort();
  protected static final String OKAPI_URL = "http://localhost:" + PORT;

  protected static final String SYSTEM_USER_NAME_ENV = "SYSTEM_USER_NAME";
  protected static final String SYSTEM_USER_PASSWORD_ENV = "SYSTEM_USER_PASSWORD";
  protected static final String SYSTEM_USER_NAME = "test-pubsub-username";
  protected static final String SYSTEM_USER_PASSWORD = "test-pubsub-password";

  static RequestSpecification spec;
  private static String useExternalDatabase;
  protected static Vertx vertx;

  @ClassRule
  public static EmbeddedKafkaCluster cluster = provisionWith(useDefaults());

  @BeforeClass
  public static void setUpClass(final TestContext context) throws Exception {
    vertx = Vertx.vertx();
    runDatabase();
    String[] hostAndPort = cluster.getBrokerList().split(":");
    System.setProperty(KAFKA_HOST, hostAndPort[0]);
    System.setProperty(KAFKA_PORT, hostAndPort[1]);
    System.setProperty(OKAPI_URL_ENV, OKAPI_URL);
    System.setProperty(SYSTEM_USER_NAME_ENV, SYSTEM_USER_NAME);
    System.setProperty(SYSTEM_USER_PASSWORD_ENV, SYSTEM_USER_PASSWORD);
    deployVerticle(context);
  }

  private static void runDatabase() throws Exception {
    useExternalDatabase = System.getProperty(
      "org.folio.pubsub.test.database",
      "embedded");

    switch (useExternalDatabase) {
      case "environment":
        System.out.println("Using environment settings");
        break;
      case "external":
        String postgresConfigPath = System.getProperty(
          "org.folio.pubsub.test.config",
          "/postgres-conf-local.json");
        PostgresClient.setConfigFilePath(postgresConfigPath);
        break;
      case "embedded":
        PostgresClient.setPostgresTester(new PostgresTesterContainer());
        break;
      default:
        String message = "No understood database choice made." +
          "Please set org.folio.pubsub.test.database" +
          "to 'external', 'environment' or 'embedded'";
        throw new Exception(message);
    }
  }

  private static void deployVerticle(final TestContext context) {
    final DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put(HTTP_PORT, PORT)
        .put("spring.configuration", "org.folio.config.TestConfig"));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(res -> {
      try {
        TenantClient tenantClient = new TenantClient(OKAPI_URL, TENANT_ID, TOKEN);
        TenantAttributes tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleTo(ModuleName.getModuleName());

        tenantClient.postTenant(tenantAttributes, context.asyncAssertSuccess(res2 -> {
          if (res2.statusCode() == 204) {
            return;
          } if (res2.statusCode() == 201) {
            JsonObject o = res2.bodyAsJsonObject();
            tenantClient.getTenantByOperationId(res2.bodyAsJson(TenantJob.class).getId(), 60000, context.asyncAssertSuccess(res3 -> {
              context.assertTrue(res3.bodyAsJson(TenantJob.class).getComplete());
              String error = res3.bodyAsJson(TenantJob.class).getError();
              // it would be better if this would actually succeed.. But we'll accept this error for now
              if (error != null) {
                context.assertEquals(format("Failed to create %s user. Received status code 404",
                  SYSTEM_USER_NAME), error);
              }
            }));
          } else {
            // if we get here and error is immediately returned from tenant init
            // it would be better if this would actually succeed.. But we'll accept this error for now
            context.assertEquals(format("Failed to create %s user. Received status code 404",
              SYSTEM_USER_NAME), res2.bodyAsString());
          }
        }));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }));
  }

  @AfterClass
  public static void tearDownClass(final TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      if (useExternalDatabase.equals("embedded")) {
        PostgresClient.stopPostgresTester();
      }
      System.clearProperty(KAFKA_HOST);
      System.clearProperty(KAFKA_PORT);
      async.complete();
    }));
  }

  @Before
  public void setUp(TestContext context) {
    clearModuleSchemaTables(context);
    clearTenantTables(context);
    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .addHeader(OKAPI_HEADER_TENANT, TENANT_ID)
      .setBaseUri("http://localhost:" + PORT)
      .addHeader("Accept", "text/plain, application/json")
      .build();
  }

  private void clearModuleSchemaTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx);
    pgClient.execute(format(DELETE_ALL_SQL, MESSAGING_MODULE_TABLE), Tuple.tuple(),event ->
      pgClient.execute(format(DELETE_ALL_SQL, EVENT_DESCRIPTOR_TABLE), Tuple.tuple(), event1 -> {
        if (event.failed()) {
          context.fail(event.cause());
        }
        async.complete();
      }));
  }

  private void clearTenantTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(AUDIT_MESSAGE_TABLE, new Criterion(), event -> {
      pgClient.delete(AUDIT_MESSAGE_PAYLOAD_TABLE, new Criterion(), event1 -> {
        if (event1.failed()) {
          context.fail(event1.cause());
        }
        async.complete();
      });
    });
  }
}
