package org.folio.services.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.folio.dao.MessagingModuleDao;
import org.folio.kafka.KafkaConfig;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.ConsumerService;
import org.folio.services.StartupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

@Component
public class StartupServiceImpl implements StartupService {

  private Vertx vertx;
  private KafkaConfig kafkaConfig;
  private MessagingModuleDao messagingModuleDao;
  private ConsumerService consumerService;

  public StartupServiceImpl(@Autowired Vertx vertx,
                            @Autowired KafkaConfig kafkaConfig,
                            @Autowired MessagingModuleDao messagingModuleDao,
                            @Autowired ConsumerService consumerService) {
    this.vertx = vertx;
    this.kafkaConfig = kafkaConfig;
    this.messagingModuleDao = messagingModuleDao;
    this.consumerService = consumerService;
  }

  @Override
  public void initSubscribers() {
    messagingModuleDao.get(new MessagingModuleFilter().withModuleRole(SUBSCRIBER).withActivated(true))
      .compose(messagingModules -> {
        messagingModules.forEach(messagingModule -> {
          OkapiConnectionParams params = new OkapiConnectionParams(vertx);
          params.setOkapiUrl(kafkaConfig.getOkapiUrl());
          params.setTenantId(messagingModule.getTenantId());
          consumerService.subscribe(messagingModule.getModuleId(), Collections.singletonList(messagingModule.getEventType()), params);
        });
        return Future.succeededFuture();
      });
  }
}
