package org.folio.util.pubsub.support;

import static org.junit.Assert.fail;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.Test;

public class PomUtilsTest {
  @Test(expected = RuntimeException.class)
  public void invalidPomShouldThrowRuntimeException() {
    try {
      String path = Path.of(getClass().getClassLoader().getResource("empty_pom.xml").toURI())
        .toFile().getAbsolutePath();
      PomUtils.getVersionRecursively(path);
    }
    catch (URISyntaxException uriSyntaxException) {
      fail();
    }
  }

  @Test(expected = RuntimeException.class)
  public void pomWithoutVersionShouldThrowRuntimeException() {
    try {
      String path = Path.of(getClass().getClassLoader().getResource("pom_without_version.xml")
        .toURI()).toFile().getAbsolutePath();
      PomUtils.getVersionRecursively(path);
    }
    catch (URISyntaxException uriSyntaxException) {
      fail();
    }
  }
}
