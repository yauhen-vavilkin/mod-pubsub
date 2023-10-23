package org.folio.config.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Arrays;

import org.junit.Test;

public class SystemUserConfigTest {

  @Test(expected = IllegalArgumentException.class)
  public void constructionFailsWhenUsernameAndPasswordAreNull() {
    testValidation(null, null, "SYSTEM_USER_NAME", "SYSTEM_USER_PASSWORD");
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructionFailsWhenUsernameIsNull() {
    testValidation(null, "testPassword", "SYSTEM_USER_NAME");
  }

  @Test(expected = IllegalArgumentException.class)
  public void constructionFailsWhenPasswordIsNull() {
    testValidation("test_user", null, "SYSTEM_USER_PASSWORD");
  }

  private void testValidation(String username, String password, String... missingVariables) {
    try {
      new SystemUserConfig(username, password);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("Failed to resolve credentials for system user. " +
        "Please provide missing system variables: " + Arrays.toString(missingVariables)));
      throw e;
    }
  }

}