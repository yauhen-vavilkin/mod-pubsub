package org.folio.services.audit;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.AuditMessagePayload;

/**
 * Audit Service Interface
 */
@ProxyGen
public interface AuditService { //NOSONAR

  String AUDIT_SERVICE_ADDRESS = "audit-service.queue";  //NOSONAR

  static AuditService create() {
    return new AuditServiceImpl();
  }

  static AuditService createProxy(Vertx vertx) {
    return new AuditServiceVertxEBProxy(vertx, AUDIT_SERVICE_ADDRESS);
  }

  /**
   * Saves {@link AuditMessage}
   *
   * @param auditMessage {@link AuditMessage} in JsonObject representation
   */
  void saveAuditMessage(JsonObject auditMessage);

  /**
   * Saves {@link AuditMessagePayload}
   *
   * @param auditMessagePayload {@link AuditMessagePayload} in JsonObject representation
   * @param tenantId            tenant id
   */
  void saveAuditMessagePayload(JsonObject auditMessagePayload, String tenantId);

}
