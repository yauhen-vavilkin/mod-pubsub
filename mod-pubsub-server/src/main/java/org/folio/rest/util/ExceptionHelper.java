package org.folio.rest.util;

import io.vertx.core.Promise;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.utils.ValidationHelper;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class ExceptionHelper {

  private static final Logger LOGGER = LogManager.getLogger();

  private ExceptionHelper() {
  }

  public static Response mapExceptionToResponse(Throwable throwable) {
    LOGGER.error(throwable.getMessage());
    if (throwable instanceof BadRequestException) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }
    if (throwable instanceof NotFoundException) {
      return Response.status(HttpStatus.SC_NOT_FOUND)
        .type(MediaType.TEXT_PLAIN)
        .entity(throwable.getMessage())
        .build();
    }
    Promise<Response> validationPromise = Promise.promise();
    ValidationHelper.handleError(throwable, validationPromise);
    if (validationPromise.future().isComplete()) {
      Response response = validationPromise.future().result();
      if (response.getStatus() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
        LOGGER.error(throwable.getMessage(), throwable);
      }
      return response;
    }
    LOGGER.error(throwable.getMessage(), throwable);
    return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .type(MediaType.TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }
}
