package org.folio.rest.util;

import io.vertx.core.Future;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_URL_HEADER;

public final class RestUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestUtil.class);

  private RestUtil() {
  }

  public static Future<HttpClientResponse> doRequest(String payload, String path, HttpMethod method, OkapiConnectionParams params) {
    Future<HttpClientResponse> future = Future.future();
    try {
      HttpClientRequest request = getHttpClient(params).requestAbs(method, params.getOkapiUrl() + path);

      CaseInsensitiveHeaders headers = new CaseInsensitiveHeaders();
      headers.add(OKAPI_URL_HEADER, params.getOkapiUrl())
        .add(OKAPI_TENANT_HEADER, params.getTenantId())
        .add(OKAPI_TOKEN_HEADER, params.getToken())
        .add("Content-type", "application/json")
        .add("Accept", "application/json, text/plain");
      headers.entries().forEach(header -> request.putHeader(header.getKey(), header.getValue()));

      request.exceptionHandler(future::fail);
      request.handler(future::complete);

      if (payload == null) {
        request.end();
      } else {
        request.end(payload);
      }
    } catch (Exception e) {
      LOGGER.error("Request to {} failed", e, path);
      future.fail(e);
    }
    return future;
  }

  private static HttpClient getHttpClient(OkapiConnectionParams params) {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(params.getTimeout());
    options.setIdleTimeout(params.getTimeout());
    return params.getVertx().createHttpClient(options);
  }
}
