package org.folio.rest.impl;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;
import org.folio.services.publish.PublishingService;

public class PublisherWorkerVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) {
    promise.handle(Future.succeededFuture());
  }
}
