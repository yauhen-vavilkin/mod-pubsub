package org.folio.rest.util;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;

public final class RestUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestUtil.class);

  private RestUtil() {
  }

  public static Future<HttpResponse<Buffer>> doRequest(String payload, String path, HttpMethod method, OkapiConnectionParams params) {
    Promise<HttpResponse<Buffer>> promise = Promise.promise();
    try {
      HttpRequest<Buffer> request = WebClient.wrap(getHttpClient(params)).requestAbs(method, params.getOkapiUrl() + path);

      MultiMap headers = new VertxHttpHeaders();
      headers.add(OKAPI_URL_HEADER, params.getOkapiUrl())
        .add(OKAPI_TENANT_HEADER, params.getTenantId())
        .add(OKAPI_TOKEN_HEADER, params.getToken())
        .add("Content-type", "application/json")
        .add("Accept", "application/json, text/plain");
      headers.entries().forEach(header -> request.putHeader(header.getKey(), header.getValue()));

      if (payload == null) {
        request.send(promise);
      } else {
        request.sendBuffer(Buffer.buffer(payload), promise);
      }
    } catch (Exception e) {
      LOGGER.error("Request to {} failed", e, path);
      promise.fail(e);
    }
    return promise.future();
  }

  private static HttpClient getHttpClient(OkapiConnectionParams params) {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(params.getTimeout());
    options.setIdleTimeout(params.getTimeout());
    return params.getVertx().createHttpClient(options);
  }
}
