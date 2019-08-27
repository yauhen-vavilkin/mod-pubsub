package org.folio.util.pubsub;

import java.io.FileNotFoundException;

/**
 * Signals that an attempt to find the messaging descriptor file (MessagingDescriptor.json) has failed.
 *
 */
public class MessagingDescriptorNotFoundException extends FileNotFoundException {

  public MessagingDescriptorNotFoundException(String s) {
    super(s);
  }
}
