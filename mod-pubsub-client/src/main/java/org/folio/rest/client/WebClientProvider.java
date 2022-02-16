package org.folio.rest.client;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import lombok.NonNull;

public class WebClientProvider {
  private static final Map<Vertx, WebClient> webClients = new HashMap<>();

  private WebClientProvider() {
  }

  public static synchronized WebClient getWebClient(@NonNull Vertx vertx) {
    return webClients.computeIfAbsent(vertx, WebClient::create);
  }
}
