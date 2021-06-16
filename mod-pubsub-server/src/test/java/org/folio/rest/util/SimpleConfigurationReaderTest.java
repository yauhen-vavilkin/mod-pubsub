package org.folio.rest.util;

import org.junit.Assert;
import org.junit.Test;

public class SimpleConfigurationReaderTest {

  @Test
  public void shouldReadValueFromSystemProperty() {
    String expectedValue = "validProperty";
    System.setProperty("test.props", expectedValue);
    String actualValue = SimpleConfigurationReader.getValue("validProperty", "test.props", null);
    Assert.assertEquals(expectedValue, actualValue);
  }

  @Test
  public void shouldReturnSpringValueIfNoSysProperty() {
    String expectedValue = "validProperty";
    System.setProperty("test.props", expectedValue);
    String actualValue = SimpleConfigurationReader.getValue(null, "test.props", null);
    Assert.assertEquals(expectedValue, actualValue);
  }

  @Test
  public void shouldReturnDefaultValueIfSysAndSpringPropertiesAreEmpty() {
    String defaultValue = "default";
    String actualValue = SimpleConfigurationReader.getValue(null, "test2.props", "default");
    Assert.assertEquals(defaultValue, actualValue);
  }
}

