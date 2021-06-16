package org.folio.rest.util;

import org.apache.commons.lang.StringUtils;

/**
 * Util for reading properties for system and spring configuration.
 */
public class SimpleConfigurationReader {
  private SimpleConfigurationReader() {
    super();
  }

  public static String getValue(String systemProperty, String kafkaProperty, String defValue) {
    if (StringUtils.isBlank(systemProperty)) {
      String value = System.getProperty(kafkaProperty, System.getenv(kafkaProperty));
      return value != null ? value : defValue;
    }
    return systemProperty;
  }
}
