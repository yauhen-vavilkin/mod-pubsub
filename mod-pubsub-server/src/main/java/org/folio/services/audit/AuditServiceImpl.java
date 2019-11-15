package org.folio.services.audit;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.dao.AuditMessageDao;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.AuditMessagePayload;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class AuditServiceImpl implements AuditService {

  @Autowired
  private AuditMessageDao auditMessageDao;

  public AuditServiceImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void saveAuditMessage(JsonObject auditMessage) {
    auditMessageDao.saveAuditMessage(auditMessage.mapTo(AuditMessage.class));
  }

  @Override
  public void saveAuditMessagePayload(JsonObject auditMessagePayload, String tenantId) {
    auditMessageDao.saveAuditMessagePayload(auditMessagePayload.mapTo(AuditMessagePayload.class), tenantId);
  }
}
