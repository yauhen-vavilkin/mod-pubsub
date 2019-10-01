package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.folio.dao.impl.MessagingModuleFilter;
import org.folio.dao.util.AuditMessageFilter;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.MessagingModule.ModuleRole;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.resource.Pubsub;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.util.ExceptionHelper;
import org.folio.services.AuditMessageService;
import org.folio.services.EventDescriptorService;
import org.folio.services.MessagingModuleService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

public class PubSubImpl implements Pubsub {

  private static final Logger LOGGER = LoggerFactory.getLogger(PubSubImpl.class);
  private final String tenantId;

  @Autowired
  private EventDescriptorService eventDescriptorService;
  @Autowired
  private MessagingModuleService messagingModuleService;
  @Autowired
  private AuditMessageService auditMessageService;

  public PubSubImpl(Vertx vertx, String tenantId) {  //NOSONAR
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    this.tenantId = TenantTool.calculateTenantId(tenantId);
  }

  @Override
  public void postPubsubEventTypes(String lang, EventDescriptor entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      validateEventDescriptor(entity)
        .compose(errors -> errors.getTotalRecords() > 0
          ? Future.succeededFuture(PostPubsubEventTypesResponse.respond422WithApplicationJson(errors))
          : eventDescriptorService.save(entity)
          .map(PostPubsubEventTypesResponse.respond201WithApplicationJson(entity, PostPubsubEventTypesResponse.headersFor201())))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to save Event Descriptor for event type '{}'", e, entity.getEventType());
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  private Future<Errors> validateEventDescriptor(EventDescriptor eventDescriptor) {
    Errors errors = new Errors()
      .withTotalRecords(0);
    return eventDescriptorService.getById(eventDescriptor.getEventType())
      .map(eventDescriptorOptional -> eventDescriptorOptional
        .map(existingDescriptor -> {
          LOGGER.error("Validation error, Event Descriptor with event type '{}' already exists", existingDescriptor.getEventType());
          return errors.withErrors(Collections.singletonList(new Error()
            .withMessage(format("Event descriptor with event type '%s' already exists", existingDescriptor.getEventType()))))
            .withTotalRecords(1);
        })
        .orElse(errors));
  }

  @Override
  public void getPubsubEventTypes(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.getAll()
        .map(GetPubsubEventTypesResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get all Event Descriptors");
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void putPubsubEventTypesByEventTypeName(String eventTypeName, String lang, EventDescriptor entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.update(entity)
        .map(PutPubsubEventTypesByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to update Event Descriptor by event type name '{}'", e, eventTypeName);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubEventTypesByEventTypeName(String eventTypeName, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.delete(eventTypeName)
        .map(DeletePubsubEventTypesByEventTypeNameResponse.respond204WithTextPlain(
          format("Event descriptor with event type name '%s' was successfully deleted", eventTypeName)))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete event descriptor by event type name '{}'", e, eventTypeName);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypesByEventTypeName(String eventTypeName, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.getById(eventTypeName)
        .map(eventDescriptorOptional -> eventDescriptorOptional
          .orElseThrow(() -> new NotFoundException(format("Event Descriptor with id '%s' not found", eventTypeName))))
        .map(GetPubsubEventTypesByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get event descriptor by event type name '{}'", e, eventTypeName);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void postPubsubEventTypesDeclarePublisher(PublisherDescriptor entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      messagingModuleService.validatePublisherDescriptor(entity)
        .compose(errors -> errors.getTotalRecords() > 0
          ? Future.succeededFuture(PostPubsubEventTypesDeclarePublisherResponse.respond400WithApplicationJson(errors))
          : messagingModuleService.savePublisher(entity, tenantId)
          .map(v -> PostPubsubEventTypesDeclarePublisherResponse.respond201()))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to create publisher", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubHistory(String startDate, String endDate, String eventId, String eventType, String correlationId, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      auditMessageService.getAuditMessages(constructAuditMessageFilter(startDate, endDate, eventId, eventType, correlationId), tenantId)
        .map(GetPubsubHistoryResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to retrieve audit messages", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubAuditMessagesPayloadByEventId(String eventId, Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      auditMessageService.getAuditMessagePayloadByEventId(eventId, tenantId)
        .map(auditMessagePayloadOptional -> auditMessagePayloadOptional
          .orElseThrow(() -> new NotFoundException(format("Couldn't find audit message payload for event %s", eventId))))
        .map(GetPubsubAuditMessagesPayloadByEventIdResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to retrieve audit message payload for event {}", e, eventId);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubEventTypesPublishersByEventTypeName(String eventTypeName, String moduleName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      MessagingModuleFilter filter = getMessagingModuleFilter(eventTypeName, PUBLISHER, tenantId);
      messagingModuleService.deleteByModuleNameAndFilter(moduleName, filter)
        .map(DeletePubsubEventTypesPublishersByEventTypeNameResponse.respond204WithTextPlain(
          format("Publisher for event type '%s' was successfully deleted", eventTypeName)))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete publisher", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypesPublishersByEventTypeName(String eventTypeName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      messagingModuleService.getByEventTypeAndRole(eventTypeName, PUBLISHER, tenantId)
        .map(GetPubsubEventTypesPublishersByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get publishers by event type '{}'", e, eventTypeName);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void postPubsubEventTypesDeclareSubscriber(SubscriberDescriptor entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      messagingModuleService.validateSubscriberDescriptor(entity)
        .compose(errors -> errors.getTotalRecords() > 0
          ? Future.succeededFuture(PostPubsubEventTypesDeclareSubscriberResponse.respond400WithApplicationJson(errors))
          : messagingModuleService.saveSubscriber(entity, tenantId)
          .map(v -> PostPubsubEventTypesDeclareSubscriberResponse.respond201()))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to create subscriber", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypesSubscribersByEventTypeName(String eventTypeName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      messagingModuleService.getByEventTypeAndRole(eventTypeName, SUBSCRIBER, tenantId)
        .map(GetPubsubEventTypesSubscribersByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get subscribers by event type '{}'", e, eventTypeName);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubEventTypesSubscribersByEventTypeName(String eventTypeName, String moduleName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      MessagingModuleFilter filter = getMessagingModuleFilter(eventTypeName, SUBSCRIBER, tenantId);
      messagingModuleService.deleteByModuleNameAndFilter(moduleName, filter)
        .map(DeletePubsubEventTypesSubscribersByEventTypeNameResponse.respond204WithTextPlain(
          format("Subscriber for event type '%s' was successfully deleted", eventTypeName)))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete subscriber", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  private MessagingModuleFilter getMessagingModuleFilter(String eventType, ModuleRole role, String tenantId) {
    MessagingModuleFilter filter = new MessagingModuleFilter();
    filter.byEventType(eventType);
    filter.byModuleRole(role);
    filter.byTenantId(tenantId);
    return filter;
  }

  private AuditMessageFilter constructAuditMessageFilter(String startDate, String endDate, String eventId, String eventType, String correlationId) {
    if (startDate == null || endDate == null) {
      throw new BadRequestException("Start date and End date are required query parameters");
    }
    String[] dateFormats = {
      DateFormatUtils.ISO_DATE_FORMAT.getPattern(),
      DateFormatUtils.ISO_DATETIME_FORMAT.getPattern(),
      DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.getPattern()
    };
    try {
      Date start = DateUtils.parseDate(startDate, dateFormats);
      Date end = DateUtils.parseDate(endDate, dateFormats);
      return new AuditMessageFilter(start, end)
        .withEventId(eventId)
        .withEventType(eventType)
        .withCorrelationId(correlationId);
    } catch (Exception e) {
      LOGGER.error("Error parsing date", e);
      throw new BadRequestException(format("Supported date formats %s, %s, %s", dateFormats[0], dateFormats[1], dateFormats[2]));
    }
  }
}
