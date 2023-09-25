package org.folio.representation;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class User {
  private String id;
  private String username;
  private String type;
  private boolean active;
  private Personal personal;
  // Everything else that we're not expecting, just in case
  private final Map<String, Object> unmapped = new HashMap<>();

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Personal getPersonal() {
    return personal;
  }

  public void setPersonal(Personal personal) {
    this.personal = personal;
  }

  @JsonAnySetter
  public void handleUnmapped(String key, Object value) {
    unmapped.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getUnmapped() {
    return unmapped;
  }

  public static class Personal {
    private String lastName;

    private final Map<String, Object> unmapped = new HashMap<>();

    public String getLastName() {
      return lastName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    @JsonAnySetter
    public void handleUnmapped(String key, Object value) {
      unmapped.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getUnmapped() {
      return unmapped;
    }
  }
}
