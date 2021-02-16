package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.liquibase.LiquibaseUtil;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.SecurityManager;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class ModTenantAPI extends TenantAPI {

  @Autowired
  private SecurityManager securityManager;

  public ModTenantAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
                           Map<String, String> headers, Context context) {
    return super.loadData(attributes, tenantId, headers, context)
      .compose(num -> {
        Vertx vertx = context.owner();
        LiquibaseUtil.initializeSchemaForTenant(vertx, tenantId);
        OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);
        return securityManager.createPubSubUser(params)
          .compose(ar -> securityManager.loginPubSubUser(params)).map(num);
      });
  }
}
