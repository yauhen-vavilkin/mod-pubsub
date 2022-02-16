package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.jaxrs.resource.Pubsub;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.util.AuditMessageFilter;
import org.folio.rest.util.ExceptionHelper;
import org.folio.rest.util.MessagingModuleFilter;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.AuditMessageService;
import org.folio.services.EventDescriptorService;
import org.folio.services.MessagingModuleService;
import org.folio.services.PublisherService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

public class PubSubImpl implements Pubsub {

  private static final Logger LOGGER = LogManager.getLogger();
  public static final String MODULE_ID_AND_ROLE_ARE_NOT_SET_MSG = "ModuleId and moduleRole are required query parameters";
  private final String tenantId;

  @Autowired
  private EventDescriptorService eventDescriptorService;
  @Autowired
  private MessagingModuleService messagingModuleService;
  @Autowired
  private AuditMessageService auditMessageService;
  @Autowired
  private PublisherService publishingService;

  public PubSubImpl(Vertx vertx, String tenantId) {  //NOSONAR
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    this.tenantId = TenantTool.calculateTenantId(tenantId);
  }

  @Override
  public void postPubsubEventTypes(String lang, EventDescriptor entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.save(entity)
        .map(PostPubsubEventTypesResponse.respond201WithApplicationJson(entity, PostPubsubEventTypesResponse.headersFor201()))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to save Event Descriptor for event type '{}'", entity.getEventType(), e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypes(String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.getAll()
        .map(GetPubsubEventTypesResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
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
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to update Event Descriptor by event type name '{}'", eventTypeName, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubEventTypesByEventTypeName(String eventTypeName, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.delete(eventTypeName)
        .map(DeletePubsubEventTypesByEventTypeNameResponse.respond204())
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete event descriptor by event type name '{}'", eventTypeName, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypesByEventTypeName(String eventTypeName, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      eventDescriptorService.getByEventType(eventTypeName)
        .map(eventDescriptorOptional -> eventDescriptorOptional
          .orElseThrow(() -> new NotFoundException(format("Event Descriptor with event type '%s' was not found", eventTypeName))))
        .map(GetPubsubEventTypesByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get event descriptor by event type name '{}'", eventTypeName, e);
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
        .onComplete(asyncResultHandler);
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
        .onComplete(asyncResultHandler);
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
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to retrieve audit message payload for event {}", eventId, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void postPubsubPublish(Event entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    LOGGER.debug("Received {} event with id '{}'", entity.getEventType(), entity.getId());
    try {
      publishingService.publishEvent(entity, tenantId)
        .map(PostPubsubPublishResponse.respond204())
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Error publishing event", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubEventTypesPublishersByEventTypeName(String eventTypeName, String moduleName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      MessagingModuleFilter filter = new MessagingModuleFilter()
        .withEventType(eventTypeName)
        .withModuleId(moduleName)
        .withTenantId(tenantId)
        .withModuleRole(PUBLISHER);
      messagingModuleService.delete(filter)
        .map(DeletePubsubEventTypesPublishersByEventTypeNameResponse.respond204())
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete publisher", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypesPublishersByEventTypeName(String eventTypeName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      messagingModuleService.get(new MessagingModuleFilter().withEventType(eventTypeName).withTenantId(tenantId).withModuleRole(PUBLISHER))
        .map(GetPubsubEventTypesPublishersByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get publishers by event type '{}'", eventTypeName, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void postPubsubEventTypesDeclareSubscriber(SubscriberDescriptor entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());
      messagingModuleService.createMissingEventTypes(entity)
        .compose(done -> messagingModuleService.saveSubscriber(entity, params)
          .map(v -> PostPubsubEventTypesDeclareSubscriberResponse.respond201()))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to register subscriber", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getPubsubEventTypesSubscribersByEventTypeName(String eventTypeName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      messagingModuleService.get(new MessagingModuleFilter().withEventType(eventTypeName).withTenantId(tenantId).withModuleRole(SUBSCRIBER))
        .map(GetPubsubEventTypesSubscribersByEventTypeNameResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to get subscribers by event type '{}'", eventTypeName, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubEventTypesSubscribersByEventTypeName(String eventTypeName, String moduleName, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      MessagingModuleFilter filter = new MessagingModuleFilter()
        .withEventType(eventTypeName)
        .withModuleId(moduleName)
        .withTenantId(tenantId)
        .withModuleRole(SUBSCRIBER);
      messagingModuleService.delete(filter)
        .map(DeletePubsubEventTypesSubscribersByEventTypeNameResponse.respond204())
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete subscriber", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deletePubsubMessagingModules(String moduleId, String moduleRole, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      getMessagingModuleFilter(moduleId, moduleRole)
        .compose(moduleFilter -> messagingModuleService.delete(moduleFilter))
        .map(DeletePubsubMessagingModulesResponse.respond204())
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.error("Failed to delete messaging module", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  private Future<MessagingModuleFilter> getMessagingModuleFilter(String moduleId, String moduleRole) {
    Promise<MessagingModuleFilter> promise = Promise.promise();
    if (moduleId == null || moduleRole == null) {
      LOGGER.error(MODULE_ID_AND_ROLE_ARE_NOT_SET_MSG);
      promise.fail(new BadRequestException(MODULE_ID_AND_ROLE_ARE_NOT_SET_MSG));
      return promise.future();
    }

    try {
      MessagingModuleFilter filter = new MessagingModuleFilter()
        .withModuleId(moduleId)
        .withModuleRole(MessagingModule.ModuleRole.fromValue(moduleRole));
      promise.complete(filter);
    } catch (IllegalArgumentException e) {
      String msg = format("Invalid moduleRole was specified: %s. Acceptable moduleRole values are: PUBLISHER, SUBSCRIBER.", moduleRole);
      LOGGER.error(msg);
      promise.fail(new BadRequestException(msg));
    }
    return promise.future();
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
      Date end = extractAndProcessEndDate(endDate, dateFormats);
      return new AuditMessageFilter(start, end)
        .withEventId(eventId)
        .withEventType(eventType)
        .withCorrelationId(correlationId);
    } catch (Exception e) {
      LOGGER.error("Error parsing date", e);
      throw new BadRequestException(format("Supported date formats %s, %s, %s", dateFormats[0], dateFormats[1], dateFormats[2]));
    }
  }

  private Date extractAndProcessEndDate(String endDate, String[] dateFormats) throws ParseException {
    Date end;
    if (checkIfISODateFormat(endDate)) {
      end = DateUtils.parseDate(endDate, new String[]{DateFormatUtils.ISO_DATE_FORMAT.getPattern()});
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(end);
      calendar.set(Calendar.HOUR_OF_DAY, 23);
      calendar.set(Calendar.MINUTE, 59);
      calendar.set(Calendar.SECOND, 59);
      calendar.set(Calendar.MILLISECOND, 999);
      return calendar.getTime();
    } else {
      end = DateUtils.parseDate(endDate, dateFormats);
      return end;
    }
  }

  private boolean checkIfISODateFormat(String endDate) {
    try {
      DateUtils.parseDate(endDate, new String[]{DateFormatUtils.ISO_DATE_FORMAT.getPattern()});
    } catch (Exception e) {
      return false;
    }
    return true;
  }
}
