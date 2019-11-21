package org.folio.util.pubsub.support;

import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;

/**
 * Container for publisher descriptor and subscriber descriptor
 */
public class DescriptorHolder {

  private PublisherDescriptor publisherDescriptor;
  private SubscriberDescriptor subscriberDescriptor;

  public PublisherDescriptor getPublisherDescriptor() {
    return publisherDescriptor;
  }

  public DescriptorHolder withPublisherDescriptor(PublisherDescriptor publisherDescriptor) {
    this.publisherDescriptor = publisherDescriptor;
    return this;
  }

  public SubscriberDescriptor getSubscriberDescriptor() {
    return subscriberDescriptor;
  }

  public DescriptorHolder withSubscriberDescriptor(SubscriberDescriptor subscriberDescriptor) {
    this.subscriberDescriptor = subscriberDescriptor;
    return this;
  }
}
