package org.folio.util.pubsub;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.rest.client.PubsubClient;
import org.folio.rest.jaxrs.model.Event;
import org.folio.rest.jaxrs.model.EventDescriptor;
import org.folio.rest.jaxrs.model.MessagingDescriptor;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.util.pubsub.exceptions.EventSendingException;
import org.folio.util.pubsub.exceptions.MessagingDescriptorNotFoundException;
import org.folio.util.pubsub.exceptions.ModuleRegistrationException;
import org.folio.util.pubsub.exceptions.ModuleUnregistrationException;
import org.folio.util.pubsub.support.DescriptorHolder;
import org.folio.util.pubsub.support.PomReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.PUBLISHER;
import static org.folio.rest.jaxrs.model.MessagingModule.ModuleRole.SUBSCRIBER;

/**
 * Util class for reading module messaging descriptors, sending messages using PubSub and register module in PubSub
 */
public class PubSubClientUtils {

  public static final String MESSAGING_CONFIG_PATH_PROPERTY = "messaging_config_path";
  private static final String MESSAGING_CONFIG_FILE_NAME = "MessagingDescriptor.json";

  private static final Logger LOGGER = LogManager.getLogger();

  private PubSubClientUtils() {
  }

  /**
   * Common method for sending async messages through PubSub module
   *
   * @param eventMessage - message with payload and metadata to send
   * @param params       - okapi connection params
   * @return - async result with boolean value. True if message was sanded successfully
   */
  public static CompletableFuture<Boolean> sendEventMessage(Event eventMessage, OkapiConnectionParams params) {
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    PubsubClient client = new PubsubClient(params.getOkapiUrl(), params.getTenantId(), params.getToken());
    try {
      client.postPubsubPublish(eventMessage, ar -> {
        if (ar.result().statusCode() == HttpStatus.HTTP_NO_CONTENT.toInt()) {
          result.complete(true);
        } else {
          EventSendingException exception = new EventSendingException(format("Error during publishing Event Message in PubSub. Status code: %s . Status message: %s ", ar.result().statusCode(), ar.result().statusMessage()));
          LOGGER.error(exception);
          result.completeExceptionally(exception);
        }
      });
    } catch (Exception e) {
      LOGGER.error("Error during sending event message to PubSub", e);
      result.completeExceptionally(e);
    }
    return result;
  }

  /**
   * Common method for registering external module in PubSub.
   *
   * @param params - okapi connection params
   * @return - async result with boolean value. True if module was registered successfully
   */
  public static CompletableFuture<Boolean> registerModule(OkapiConnectionParams params) {
    CompletableFuture<Boolean> result = CompletableFuture.completedFuture(false);
    PubsubClient client = new PubsubClient(params.getOkapiUrl(), params.getTenantId(), params.getToken());
    try {
      LOGGER.info("Reading MessagingDescriptor.json");
      DescriptorHolder descriptorHolder = readMessagingDescriptor();
      if (descriptorHolder.getPublisherDescriptor() != null &&
        isNotEmpty(descriptorHolder.getPublisherDescriptor().getEventDescriptors())) {
        LOGGER.info("Registering events for publishers");
        List<EventDescriptor> eventDescriptors = descriptorHolder.getPublisherDescriptor().getEventDescriptors();
        result = registerEventTypes(client, eventDescriptors)
          .thenCompose(ar -> registerPublishers(client, descriptorHolder.getPublisherDescriptor()));
      }
      if (descriptorHolder.getSubscriberDescriptor() != null &&
        isNotEmpty(descriptorHolder.getSubscriberDescriptor().getSubscriptionDefinitions())) {
        result = result.thenCompose(ar -> registerSubscribers(client, descriptorHolder.getSubscriberDescriptor()));
      }
    } catch (Exception e) {
      LOGGER.error("Error during registration module in PubSub", e);
      result = CompletableFuture.failedFuture(e);
    }
    return result;
  }

  private static CompletableFuture<Void> registerEventTypes(PubsubClient client, List<EventDescriptor> events) {
    List<CompletableFuture<Boolean>> list = new ArrayList<>();
    try {
      for (EventDescriptor eventDescriptor : events) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        client.postPubsubEventTypes(null, eventDescriptor, ar -> {
          if (ar.result().statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
            future.complete(true);
          } else {
            ModuleRegistrationException exception = new ModuleRegistrationException(format("EventDescriptor was not registered for eventType: %s . Status code: %s", eventDescriptor.getEventType(), ar.result().statusCode()));
            LOGGER.error(exception);
            future.completeExceptionally(exception);
          }
        });
        list.add(future);
      }
    } catch (Exception e) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      LOGGER.error("Module's events were not registered in PubSub.", e);
      future.completeExceptionally(e);
      return future;
    }
    return CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()]));
  }

  private static CompletableFuture<Boolean> registerSubscribers(PubsubClient client, SubscriberDescriptor descriptor) {
    LOGGER.info("Registering module's subscribers");
    CompletableFuture<Boolean> subscribersResult = new CompletableFuture<>();
    try {
      client.postPubsubEventTypesDeclareSubscriber(descriptor, ar -> {
        if (ar.result().statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Module's subscribers were successfully registered");
          subscribersResult.complete(true);
        } else {
          ModuleRegistrationException exception = new ModuleRegistrationException("Module's subscribers were not registered in PubSub. HTTP status: " + ar.result().statusCode());
          LOGGER.error(exception);
          subscribersResult.completeExceptionally(exception);
        }
      });
    } catch (Exception e) {
      LOGGER.error("Module's subscribers were not registered in PubSub.", e);
      subscribersResult.completeExceptionally(e);
    }
    return subscribersResult;
  }

  private static CompletableFuture<Boolean> registerPublishers(PubsubClient client, PublisherDescriptor descriptor) {
    LOGGER.info("Registering module's publishers");
    CompletableFuture<Boolean> publishersResult = new CompletableFuture<>();
    try {
      client.postPubsubEventTypesDeclarePublisher(descriptor, ar -> {
        if (ar.result().statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Module's publishers were successfully registered");
          publishersResult.complete(true);
        } else {
          ModuleRegistrationException exception = new ModuleRegistrationException("Module's publishers were not registered in PubSub. HTTP status: " + ar.result().statusCode());
          LOGGER.error(exception);
          publishersResult.completeExceptionally(exception);
        }
      });
    } catch (Exception e) {
      LOGGER.error("Module's publishers were not registered in PubSub.", e);
      publishersResult.completeExceptionally(e);
    }
    return publishersResult;
  }

  /**
   * Util method to unregister external module in PubSub.
   *
   * @param params - okapi connection params
   * @return future with true if module was unregistered successfully
   */
  public static CompletableFuture<Boolean> unregisterModule(OkapiConnectionParams params) {
    PubsubClient client = new PubsubClient(params.getOkapiUrl(), params.getTenantId(), params.getToken());
    String moduleId = getModuleId();

    return unregisterModuleByIdAndRole(client, moduleId, PUBLISHER)
      .thenCompose(ar -> unregisterModuleByIdAndRole(client, moduleId, SUBSCRIBER));
  }

  private static CompletableFuture<Boolean> unregisterModuleByIdAndRole(PubsubClient client, String moduleId, MessagingModule.ModuleRole moduleRole) {
    Promise<Boolean> promise = Promise.promise();
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    try {
      LOGGER.info("Trying to unregister module with name '{}' as {}", moduleId, moduleRole);
      client.deletePubsubMessagingModules(moduleId, moduleRole.value(), response -> {
        if (response.result().statusCode() == HttpStatus.HTTP_NO_CONTENT.toInt()) {
          LOGGER.info("Module {} was successfully unregistered as '{}'", moduleId, moduleRole);
          future.complete(true);
        } else {
          String msg = format("Module %s was not unregistered as '%s' in PubSub. HTTP status: %s", moduleId, moduleRole, response.result().statusCode());
          LOGGER.error(msg);
          future.completeExceptionally(new ModuleUnregistrationException(msg));
        }
      });
    } catch (Exception e) {
      LOGGER.error("Module was not unregistered as '{}' in PubSub.", moduleRole, e);
      promise.fail(e);
      future.completeExceptionally(e);
    }
    return future;
  }

  /**
   * Reads messaging descriptor file 'MessagingDescriptor.json' and returns {@link DescriptorHolder} that contains
   * descriptors for module registration as publisher and subscriber.
   * At first, messaging descriptor is searched in directory by path specified in 'messaging_config_path' system property,
   * if file was not found then it is searched in classpath.
   * Location for descriptor directory can be specified as absolute or relative path.
   * If descriptor directory path is relative then file is searched relative to classpath.
   * Throws {@link MessagingDescriptorNotFoundException} when messaging descriptor file was not found.
   *
   * @return {@link DescriptorHolder}
   * @throws MessagingDescriptorNotFoundException if messaging descriptor file was not found
   * @throws IOException                          if a low-level I/O problem (unexpected end-of-input) occurs while reading file
   * @throws IllegalArgumentException             if parsing file problems occurs (file contains invalid json structure)
   */
  static DescriptorHolder readMessagingDescriptor() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      MessagingDescriptor messagingDescriptor = objectMapper.readValue(getMessagingDescriptorInputStream(), MessagingDescriptor.class);
      String moduleId = getModuleId();

      LOGGER.info("Reading messaging descriptor for module {}", moduleId);

      return new DescriptorHolder()
        .withPublisherDescriptor(new PublisherDescriptor()
          .withModuleId(moduleId)
          .withEventDescriptors(messagingDescriptor.getPublications()))
        .withSubscriberDescriptor(new SubscriberDescriptor()
          .withModuleId(moduleId)
          .withSubscriptionDefinitions(messagingDescriptor.getSubscriptions()));
    } catch (JsonParseException | JsonMappingException e) {
      String errorMessage = "Can not read messaging descriptor, cause: " + e.getMessage();
      LOGGER.error(errorMessage);
      throw new IllegalArgumentException(e);
    }
  }

  private static InputStream getMessagingDescriptorInputStream() throws MessagingDescriptorNotFoundException {
    return Optional.ofNullable(System.getProperty(MESSAGING_CONFIG_PATH_PROPERTY))
      .flatMap(PubSubClientUtils::getFileInputStreamByParentPath)
      // returns empty Optional when file not found or Optional<Optional<InputStream>> with input stream of found file in otherwise
      .map(Optional::of)
      // looking for a file in class path when file was not found by parent path
      .orElseGet(() -> getFileInputStreamFromClassPath(MESSAGING_CONFIG_FILE_NAME))
      .orElseThrow(() -> new MessagingDescriptorNotFoundException("Messaging descriptor file 'MessagingDescriptor.json' not found"));
  }

  private static Optional<InputStream> getFileInputStreamByParentPath(String parentPath) {
    if (Paths.get(parentPath).isAbsolute()) {
      return getFileInputStreamByAbsoluteParentPath(parentPath);
    }
    String fullRelativeFilePath = new StringBuilder().append(parentPath).append(File.separatorChar).append(MESSAGING_CONFIG_FILE_NAME).toString();
    return getFileInputStreamFromClassPath(fullRelativeFilePath);
  }

  private static Optional<InputStream> getFileInputStreamByAbsoluteParentPath(String absoluteParentPath) {
    File file = new File(absoluteParentPath, MESSAGING_CONFIG_FILE_NAME);
    try {
      return Optional.of(new FileInputStream(file));
    } catch (FileNotFoundException e) {
      return Optional.empty();
    }
  }

  private static Optional<InputStream> getFileInputStreamFromClassPath(String path) {
    String preparedPath = path.replace('\\', '/');
    InputStream fileStream = PubSubClientUtils.class.getClassLoader().getResourceAsStream(preparedPath);
    if (fileStream == null) {
      return Optional.empty();
    }
    return Optional.of(fileStream);
  }

  public static String getModuleId() {
    return format("%s-%s", PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
  }
}
