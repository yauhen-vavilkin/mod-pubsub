package org.folio.rest.impl;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;
import org.folio.services.publish.PublishingService;

public class PublisherWorkerVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) throws Exception {
    initPublishingService(vertx);
    promise.handle(Future.succeededFuture());
  }

  private void initPublishingService(Vertx vertx) {
    new ServiceBinder(vertx)
      .setAddress(PublishingService.PUBLISHING_SERVICE_ADDRESS)
      .register(PublishingService.class, PublishingService.create(vertx));
  }
}
