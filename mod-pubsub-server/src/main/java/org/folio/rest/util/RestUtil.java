package org.folio.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.util.Map;

/**
 * Util class with static method for sending http request
 */
public final class RestUtil {

  public static class WrappedResponse {
    private final int code;
    private final String body;
    private JsonObject json;
    private final HttpResponse<Buffer> response;

    WrappedResponse(int code, String body,
                    HttpResponse<Buffer> response) {
      this.code = code;
      this.body = body;
      this.response = response;
      try {
        json = new JsonObject(body);
      } catch (Exception e) {
        json = null;
      }
    }

    public int getCode() {
      return code;
    }

    public String getBody() {
      return body;
    }

    public HttpResponse<Buffer> getResponse() {
      return response;
    }

    public JsonObject getJson() {
      return json;
    }
  }

  private RestUtil() {
  }

  /**
   * Create http request
   *
   * @param url     - url for http request
   * @param method  - http method
   * @param payload - body of request
   * @return - async http response
   */
  public static <T> Future<WrappedResponse> doRequest(OkapiConnectionParams params, String url,
                                                      HttpMethod method, T payload) {
    Promise<WrappedResponse> promise = Promise.promise();
    try {
      Map<String, String> headers = params.getHeaders();
      String requestUrl = params.getOkapiUrl() + url;
      WebClient client = WebClient.wrap(getHttpClient(params));

      HttpRequest<Buffer> request = client.requestAbs(method, requestUrl);
      if (headers != null) {
        headers.put("Content-type", "application/json");
        headers.put("Accept", "application/json, text/plain");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          request.putHeader(entry.getKey(), entry.getValue());
        }
      }
      if (method == HttpMethod.PUT || method == HttpMethod.POST) {
        request.sendBuffer(Buffer.buffer(payload instanceof String ? (String) payload : new ObjectMapper().writeValueAsString(payload)), handleResponse(promise));
      } else {
        request.send(handleResponse(promise));
      }
      return promise.future();
    } catch (Exception e) {
      promise.fail(e);
      return promise.future();
    }
  }

  private static Handler<AsyncResult<HttpResponse<Buffer>>> handleResponse(Promise<WrappedResponse> promise) {
    return ar -> {
      if (ar.succeeded()) {
        WrappedResponse wr = new WrappedResponse(ar.result().statusCode(), ar.result().bodyAsString(), ar.result());
        promise.complete(wr);
      } else {
        promise.fail(ar.cause());
      }
    };
  }

  /**
   * Prepare HttpClient from OkapiConnection params
   *
   * @param params - Okapi connection params
   * @return - Vertx Http Client
   */
  private static HttpClient getHttpClient(OkapiConnectionParams params) {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(params.getTimeout());
    options.setIdleTimeout(params.getTimeout());
    return params.getVertx() != null ? params.getVertx().createHttpClient(options) : Vertx.currentContext().owner().createHttpClient(options);
  }
}
