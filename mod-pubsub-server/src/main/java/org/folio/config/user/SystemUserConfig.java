package org.folio.config.user;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Component
public class SystemUserConfig {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String SYSTEM_USER_NAME_VAR = "SYSTEM_USER_NAME";
  private static final String SYSTEM_USER_PASSWORD_VAR = "SYSTEM_USER_PASSWORD";

  @Getter
  private final String name;
  private final String password;

  public SystemUserConfig(@Value("${SYSTEM_USER_NAME:#{null}}") String name,
    @Value("${SYSTEM_USER_PASSWORD:#{null}}") String password) {

    validateCredentials(name, password);
    this.name = name;
    this.password = password;
  }

  public JsonObject getUserCredentialsJson() {
    return new JsonObject()
      .put("username", name)
      .put("password", password);
  }

  private static void validateCredentials(String username, String password) {
    LOGGER.info("validateCredentials:: validating system user credentials");
    List<String> missingVariables = new ArrayList<>();
    if (isBlank(username)) {
      LOGGER.error("validateCredentials:: username is blank");
      missingVariables.add(SYSTEM_USER_NAME_VAR);
    }
    if (isBlank(password)) {
      LOGGER.error("validateCredentials:: password is blank");
      missingVariables.add(SYSTEM_USER_PASSWORD_VAR);
    }
    if (!missingVariables.isEmpty()) {
      String errorMessage = "Invalid system user credentials. " +
        "Please provide non-blank values for system variables: " + missingVariables;
      LOGGER.error("validateCredentials:: {}", errorMessage);
      throw new IllegalArgumentException(errorMessage);
    }
    LOGGER.info("validateCredentials:: system user credentials are valid");
  }

}
