package org.folio.util.pubsub.exceptions;

/**
 * Signals that an attempt to send Event message has failed.
 */
public class EventSendingException extends Exception {

  public EventSendingException(String s) {
    super(s);
  }
}
