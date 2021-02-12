package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;
import org.folio.config.ApplicationConfig;
import org.folio.liquibase.LiquibaseUtil;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.StartupService;
import org.folio.services.audit.AuditService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class InitAPIImpl implements InitAPI {

  private static final String MODULE_CONFIGURATION_SCHEMA = "pubsub_config";

  @Autowired
  private StartupService startupService;

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    try {
      SpringContextUtil.init(vertx, context, ApplicationConfig.class);
      SpringContextUtil.autowireDependencies(this, context);
      LiquibaseUtil.initializeSchemaForModule(vertx, MODULE_CONFIGURATION_SCHEMA);
      startupService.initSubscribers();
      initAuditService(vertx);
      DeploymentOptions options = new DeploymentOptions().setWorker(true);
      vertx.deployVerticle(new PublisherWorkerVerticle(), options)
        .onSuccess(v -> handler.handle(Future.succeededFuture(true)))
        .onFailure(e -> handler.handle(Future.failedFuture(e)));
    } catch (Exception e) {
      handler.handle(Future.failedFuture(e));
    }
  }

  private void initAuditService(Vertx vertx) {
    new ServiceBinder(vertx)
      .setAddress(AuditService.AUDIT_SERVICE_ADDRESS)
      .register(AuditService.class, AuditService.create());
  }

}
