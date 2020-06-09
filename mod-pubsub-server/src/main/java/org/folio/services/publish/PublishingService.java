package org.folio.services.publish;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Event;

/**
 * Publishing Service Interface
 */
@ProxyGen
public interface PublishingService { //NOSONAR

  String PUBLISHING_SERVICE_ADDRESS = "publishing-service.queue";  //NOSONAR

  static PublishingService create(Vertx vertx) {
    return new PublishingServiceImpl(vertx);
  }

  static PublishingService createProxy(Vertx vertx) {
    return new PublishingServiceVertxEBProxy(vertx, PUBLISHING_SERVICE_ADDRESS);
  }

  /**
   * Publishes an event to an appropriate topic
   *
   * @param event    {@link Event} in JsonObject representation
   * @param tenantId tenant id
   */
  void sendEvent(JsonObject event, String tenantId);

}
