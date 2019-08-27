package org.folio.util.pubsub;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.MessagingDescriptor;
import org.folio.rest.jaxrs.model.PublisherDescriptor;
import org.folio.rest.jaxrs.model.SubscriberDescriptor;
import org.folio.rest.tools.PomReader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Util class for reading module messaging descriptor
 */
public class PubSubClientUtils {

  public static final String MESSAGING_CONFIG_PATH_PROPERTY = "messaging_config_path";
  private static final String MESSAGING_CONFIG_FILE_NAME = "MessagingDescriptor.json";

  private static final Logger LOGGER = LoggerFactory.getLogger(PubSubClientUtils.class);

  private PubSubClientUtils() {
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
   * @throws IOException if a low-level I/O problem (unexpected end-of-input) occurs while readind file
   * @throws IllegalArgumentException if parsing file problems occurs (file contains invalid json structure)
   */
  public static DescriptorHolder readMessagingDescriptor() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      File messagingDescriptorFile = getMessagingDescriptorFile();
      MessagingDescriptor messagingDescriptor = objectMapper.readValue(messagingDescriptorFile, MessagingDescriptor.class);

      return new DescriptorHolder()
        .withPublisherDescriptor(new PublisherDescriptor()
            .withModuleName(PomReader.INSTANCE.getModuleName())
            .withEventDescriptors(messagingDescriptor.getPublications()))
        .withSubscriberDescriptor(new SubscriberDescriptor()
          .withModuleName(PomReader.INSTANCE.getModuleName())
          .withSubscriptionDefinitions(messagingDescriptor.getSubscriptions()));
    } catch (JsonParseException | JsonMappingException e) {
      String errorMessage = "Can not read messaging descriptor, cause: " + e.getMessage();
      LOGGER.error(errorMessage);
      throw new IllegalArgumentException(e);
    }
  }

  private static File getMessagingDescriptorFile() throws MessagingDescriptorNotFoundException {
    return Optional.ofNullable(System.getProperty(MESSAGING_CONFIG_PATH_PROPERTY))
      // returns empty Optional when file was not found or Optional<File> with found file in otherwise
      .flatMap(PubSubClientUtils::getFileByParentPath)
      // returns empty Optional when file not found or Optional<Optional<File>> with found file in otherwise
      .map(Optional::of)
      // looking for a file in class path when file was not found by parent path
      .orElseGet(() -> getFileFromClassPath(MESSAGING_CONFIG_FILE_NAME))
      .orElseThrow(() -> new MessagingDescriptorNotFoundException("Messaging descriptor file 'MessagingDescriptor.json' not found"));
  }

  private static Optional<File> getFileByParentPath(String parentPath) {
    if (Paths.get(parentPath).isAbsolute()) {
      return getFileByAbsoluteParentPath(parentPath);
    }
    String fullRelativeFilePath = new StringBuilder().append(parentPath).append(File.separatorChar).append(MESSAGING_CONFIG_FILE_NAME).toString();
    return getFileFromClassPath(fullRelativeFilePath);
  }

  private static Optional<File> getFileByAbsoluteParentPath(String absoluteParentPath) {
    File file = new File(absoluteParentPath, MESSAGING_CONFIG_FILE_NAME);
    if (file.exists()) {
      return Optional.of(file);
    }
    return Optional.empty();
  }

  private static Optional<File> getFileFromClassPath(String path) {
    String preparedPath = path.replace('\\', '/');
    URL fileUrl = PubSubClientUtils.class.getClassLoader().getResource(preparedPath);
    if (fileUrl == null) {
      return Optional.empty();
    }
    return Optional.of(new File(fileUrl.getFile()));
  }

}
