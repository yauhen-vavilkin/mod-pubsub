package org.folio.util.pubsub.exceptions;

/**
 * Signals that some errors were occurred during module unregistration in PubSub.
 */
public class ModuleUnregistrationException extends RuntimeException {

  public ModuleUnregistrationException(String s) {
    super(s);
  }
}
