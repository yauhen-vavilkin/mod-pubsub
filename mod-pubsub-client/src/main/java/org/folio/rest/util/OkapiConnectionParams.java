package org.folio.rest.util;

import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;

@RequiredArgsConstructor
@Getter
@Setter
public final class OkapiConnectionParams {

  public static final String OKAPI_URL_HEADER = "x-okapi-url";
  public static final String OKAPI_TENANT_HEADER = "x-okapi-tenant";
  public static final String OKAPI_TOKEN_HEADER = "x-okapi-token";
  private String okapiUrl;
  private String tenantId;
  private String token;
  private Map<String, String> headers;
  private final Vertx vertx;
  private int timeout = 2000;

  public OkapiConnectionParams(Map<String, String> okapiHeaders, Vertx vertx) {
    this.okapiUrl = okapiHeaders.getOrDefault(OKAPI_URL_HEADER, "localhost");
    this.tenantId = okapiHeaders.getOrDefault(OKAPI_TENANT_HEADER, "");
    this.token = okapiHeaders.getOrDefault(OKAPI_TOKEN_HEADER, "dummy");
    this.headers = okapiHeaders;
    this.vertx = vertx;
  }
}
