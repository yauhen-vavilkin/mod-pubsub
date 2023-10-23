package org.folio.config.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

@Component
public class SystemUserConfig {
  private final String name;
  private final String password;

  public SystemUserConfig(@Value("${SYSTEM_USER_NAME:pub-sub}") String name,
    @Value("${SYSTEM_USER_PASSWORD:#{null}}") String password) {

    if (password == null) {
      throw new IllegalArgumentException(String.format(
        "Please provide password for user '%s' using system variable SYSTEM_USER_PASSWORD", name));
    }

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
}
