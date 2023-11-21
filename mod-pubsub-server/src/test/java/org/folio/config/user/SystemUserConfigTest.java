package org.folio.config.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Arrays;

import org.junit.Test;

public class SystemUserConfigTest {
  private static final String SYSTEM_USER_NAME_VAR = "SYSTEM_USER_NAME";
  private static final String SYSTEM_USER_PASSWORD_VAR = "SYSTEM_USER_PASSWORD";
  private static final String VALID_USERNAME = "test-username";
  private static final String VALID_PASSWORD = "test-password";
  private static final String EMPTY_STRING = "";
  private static final String BLANK_STRING = "   ";

  @Test
  public void validSystemUserCredentials() {
    SystemUserConfig config = new SystemUserConfig(VALID_USERNAME, VALID_PASSWORD, true);
    assertThat(config.getName(), is(VALID_USERNAME));
  }

  @Test
  public void invalidSystemUserCredentials() {
    testFailedValidation(VALID_USERNAME, null, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(VALID_USERNAME, EMPTY_STRING, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(VALID_USERNAME, BLANK_STRING, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(null, VALID_PASSWORD, SYSTEM_USER_NAME_VAR);
    testFailedValidation(null, null, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(null, EMPTY_STRING, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(null, BLANK_STRING, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(EMPTY_STRING, VALID_PASSWORD, SYSTEM_USER_NAME_VAR);
    testFailedValidation(EMPTY_STRING, null, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(EMPTY_STRING, EMPTY_STRING, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(EMPTY_STRING, BLANK_STRING, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(BLANK_STRING, VALID_PASSWORD, SYSTEM_USER_NAME_VAR);
    testFailedValidation(BLANK_STRING, null, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(BLANK_STRING, EMPTY_STRING, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
    testFailedValidation(BLANK_STRING, BLANK_STRING, SYSTEM_USER_NAME_VAR, SYSTEM_USER_PASSWORD_VAR);
  }

  private static void testFailedValidation(String username, String password,
                                           String... missingVariables) {

    try {
      new SystemUserConfig(username, password, true);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("Invalid system user credentials. " +
        "Please provide non-blank values for system variables: " + Arrays.toString(missingVariables)));
      return;
    }
    throw new AssertionError("IllegalArgumentException was expected");
  }

}
