package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.resource.Pubsub;

import javax.ws.rs.core.Response;
import java.util.Map;

public class PubSubImpl implements Pubsub {

  @Override
  public void getPubsub(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(Future.succeededFuture(GetPubsubResponse.respond200()));
  }
}
