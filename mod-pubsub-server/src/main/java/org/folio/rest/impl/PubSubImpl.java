package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.resource.Pubsub;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.util.ExceptionHelper;
import org.folio.services.EventDescriptorService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;

public class PubSubImpl implements Pubsub {

  private static final Logger LOGGER =  LoggerFactory.getLogger(PubSubImpl.class);
  private final String tenantId;

  @Autowired
  private EventDescriptorService eventDescriptorService;

  public PubSubImpl(Vertx vertx, String tenantId) {
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
}
