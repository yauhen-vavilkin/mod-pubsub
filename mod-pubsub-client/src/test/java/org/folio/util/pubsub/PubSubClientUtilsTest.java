package org.folio.util.pubsub;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.folio.util.pubsub.PubSubClientUtils.MESSAGING_CONFIG_PATH_PROPERTY;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;

public class PubSubClientUtilsTest {

  private static final String MESSAGE_DESCRIPTOR_PATH_WITH_INVALID_FIELD = "config/with_invalid_field";
  private static final String VALID_MESSAGE_DESCRIPTOR_PATH = "config\\valid_config\\";

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @After
  public void tearDown() {
    System.clearProperty(MESSAGING_CONFIG_PATH_PROPERTY);
  }

  @Test
  public void shouldReadMessagingDescriptorByDefaultPathAndReturnFilledDescriptorHolder() throws IOException {
    DescriptorHolder descriptorHolder = PubSubClientUtils.readMessagingDescriptor();

    Assert.assertNotNull(descriptorHolder.getPublisherDescriptor());
    Assert.assertNotNull(descriptorHolder.getSubscriberDescriptor());
    Assert.assertThat(descriptorHolder.getPublisherDescriptor().getModuleId(), not(isEmptyOrNullString()));
    Assert.assertThat(descriptorHolder.getPublisherDescriptor().getEventDescriptors().size(), is(1));
    Assert.assertThat(descriptorHolder.getSubscriberDescriptor().getModuleId(), not(isEmptyOrNullString()));
    Assert.assertThat(descriptorHolder.getSubscriberDescriptor().getSubscriptionDefinitions().size(), is(1));
  }

  @Test
  public void shouldReadMessagingDescriptorByPathFromSystemProperty() throws IOException {
    System.setProperty(MESSAGING_CONFIG_PATH_PROPERTY, VALID_MESSAGE_DESCRIPTOR_PATH);

    DescriptorHolder descriptorHolder = PubSubClientUtils.readMessagingDescriptor();

    Assert.assertNotNull(descriptorHolder.getPublisherDescriptor());
    Assert.assertNotNull(descriptorHolder.getSubscriberDescriptor());
    Assert.assertThat(descriptorHolder.getPublisherDescriptor().getModuleId(), not(isEmptyOrNullString()));
    Assert.assertThat(descriptorHolder.getPublisherDescriptor().getEventDescriptors().size(), is(1));
    Assert.assertThat(descriptorHolder.getSubscriberDescriptor().getModuleId(), not(isEmptyOrNullString()));
    Assert.assertThat(descriptorHolder.getSubscriberDescriptor().getSubscriptionDefinitions().size(), is(2));
  }

  @Test
  public void shouldReadMessagingDescriptorFromClassPathWhenFileWasNotFoundByPathFromSystemProperty() throws IOException {
    File descriptorParentFolder = temporaryFolder.newFolder();
    System.setProperty(MESSAGING_CONFIG_PATH_PROPERTY, descriptorParentFolder.getAbsolutePath());

    DescriptorHolder descriptorHolder = PubSubClientUtils.readMessagingDescriptor();

    Assert.assertNotNull(descriptorHolder.getPublisherDescriptor());
    Assert.assertNotNull(descriptorHolder.getSubscriberDescriptor());
    Assert.assertThat(descriptorHolder.getPublisherDescriptor().getModuleId(), not(isEmptyOrNullString()));
    Assert.assertThat(descriptorHolder.getPublisherDescriptor().getEventDescriptors().size(), is(1));
    Assert.assertThat(descriptorHolder.getSubscriberDescriptor().getModuleId(), not(isEmptyOrNullString()));
    Assert.assertThat(descriptorHolder.getSubscriberDescriptor().getSubscriptionDefinitions().size(), is(1));
  }

  @Test
  public void shouldThrowExceptionWhenReadInvalidMessagingDescriptor() throws IOException {
    exceptionRule.expect(IllegalArgumentException.class);
    System.setProperty(MESSAGING_CONFIG_PATH_PROPERTY, MESSAGE_DESCRIPTOR_PATH_WITH_INVALID_FIELD);

    PubSubClientUtils.readMessagingDescriptor();
  }

}
