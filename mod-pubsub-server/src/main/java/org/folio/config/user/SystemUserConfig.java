package org.folio.config.user;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

@Component
public class SystemUserConfig {
  private final String name;
  private final String password;

  public SystemUserConfig(@Value("${SYSTEM_USER_NAME:#{null}}") String name,
    @Value("${SYSTEM_USER_PASSWORD:#{null}}") String password) {

    validateCredentials(name, password);
    this.name = name;
    this.password = password;
  }

  public String getName() {
    return name;
  }

  public JsonObject getUserCredentialsJson() {
    return new JsonObject()
      .put("username", name)
      .put("password", password);
  }

  private static void validateCredentials(String username, String password) {
    List<String> missingVariables = new ArrayList<>();
    if (username == null) {
      missingVariables.add("SYSTEM_USER_NAME");
    }
    if (password == null) {
      missingVariables.add("SYSTEM_USER_PASSWORD");
    }

    if (!missingVariables.isEmpty()) {
      throw new IllegalArgumentException("Failed to resolve credentials for system user. " +
        "Please provide missing system variables: " + missingVariables);
    }
  }
}
