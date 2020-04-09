package org.folio.services.publish;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;
import org.folio.config.ApplicationConfig;
import org.folio.kafka.PubSubConfig;
import org.folio.rest.jaxrs.model.AuditMessage;
import org.folio.rest.jaxrs.model.Event;
import org.folio.services.audit.AuditService;
import org.folio.services.impl.KafkaProducerManager;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.services.util.AuditUtil.constructJsonAuditMessage;

public class PublishingServiceImpl implements PublishingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PublishingServiceImpl.class);

  private static final int THREAD_POOL_SIZE =
    Integer.parseInt(MODULE_SPECIFIC_ARGS.getOrDefault("event.publishing.thread.pool.size", "20"));

  @Autowired
  private KafkaProducerManager manager;
  private WorkerExecutor executor;
  private AuditService auditService;

  public PublishingServiceImpl(Vertx vertx) {
    SpringContextUtil.init(vertx, Vertx.currentContext(), ApplicationConfig.class);
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    this.auditService = AuditService.createProxy(vertx);
    this.executor = vertx.createSharedWorkerExecutor("event-publishing-thread-pool", THREAD_POOL_SIZE);
  }

  @Override
  public void sendEvent(JsonObject event, String tenantId) {
    Event eventObject = event.mapTo(Event.class);
    PubSubConfig config = new PubSubConfig(tenantId, eventObject.getEventType());
    executor.executeBlocking(future -> {
        try {
          manager.getKafkaProducer().write(new KafkaProducerRecordImpl<>(config.getTopicName(), event.encode()), done -> {
            if (done.succeeded()) {
              LOGGER.info("Sent {} event with id '{}' to topic {}",  event.getString("eventType"), event.getString("id"), config.getTopicName());
              auditService.saveAuditMessage(constructJsonAuditMessage(eventObject, tenantId, AuditMessage.State.PUBLISHED));
            } else {
              LOGGER.error("Event was not sent", done.cause());
              auditService.saveAuditMessage(constructJsonAuditMessage(eventObject, tenantId, AuditMessage.State.REJECTED));
            }
          });
        } catch (Exception e) {
          LOGGER.error("Error publishing event", e);
          auditService.saveAuditMessage(constructJsonAuditMessage(eventObject, tenantId, AuditMessage.State.REJECTED));
        }
      }
      , null);
  }
}
