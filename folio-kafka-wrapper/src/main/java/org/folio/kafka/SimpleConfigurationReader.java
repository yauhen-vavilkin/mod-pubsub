package org.folio.kafka;


public class SimpleConfigurationReader {
  private SimpleConfigurationReader() {
    super();
  }

  public static String getValue(String key, String defValue) {
    String value = System.getenv(key);
    return value != null ? value : defValue;
  }
}
