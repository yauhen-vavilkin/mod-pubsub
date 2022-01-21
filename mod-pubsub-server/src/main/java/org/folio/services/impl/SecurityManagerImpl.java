package org.folio.services.impl;

import com.google.common.io.Resources;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.config.user.SystemUserConfig;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.representation.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.SecurityManager;
import org.folio.services.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.http.HttpMethod.PUT;
import static io.vertx.core.json.Json.encode;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.RestUtil.doRequest;

@Component
public class SecurityManagerImpl implements SecurityManager {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final String LOGIN_URL = "/authn/login";
  private static final String USERS_URL = "/users";
  private static final String CREDENTIALS_URL = "/authn/credentials";
  private static final String PERMISSIONS_URL = "/perms/users";
  private static final String PERMISSIONS_FILE_PATH = "permissions/pubsub-user-permissions.csv";
  private static final String USER_LAST_NAME = "System";

  private Vertx vertx;
  private Cache cache;
  private SystemUserConfig systemUserConfig;

  public SecurityManagerImpl(@Autowired Vertx vertx, @Autowired Cache cache,
    @Autowired SystemUserConfig systemUserConfig) {

    this.vertx = vertx;
    this.cache = cache;
    this.systemUserConfig = systemUserConfig;
  }

  @Override
  public Future<Boolean> loginPubSubUser(OkapiConnectionParams params) {
    params.setToken(EMPTY);

    String token = cache.getToken(params.getTenantId());
    if (!StringUtils.isEmpty(token)) {
      return Future.succeededFuture(true);
    }

    return Future.succeededFuture(systemUserConfig.getUserCredentialsJson())
      .compose(userCredentials -> doRequest(params, LOGIN_URL, HttpMethod.POST, userCredentials.encode()))
      .compose(response -> {
        if (response.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Logged in {} user", systemUserConfig.getName());
          cache.addToken(params.getTenantId(), response.getResponse().getHeader(OKAPI_TOKEN_HEADER));
          return Future.succeededFuture(true);
        }
        LOGGER.error("{} user was not logged in, received status {}", systemUserConfig.getName(),
          response.getCode());
        return Future.succeededFuture(false);
      });
  }

  @Override
  public Future<String> getJWTToken(OkapiConnectionParams params) {
    String token = cache.getToken(params.getTenantId());
    if (StringUtils.isEmpty(token)) {
      return loginPubSubUser(params).compose(isLoggedIn -> {
        if (BooleanUtils.isTrue(isLoggedIn)) {
          return Future.succeededFuture(cache.getToken(params.getTenantId()));
        }
        return Future.failedFuture(format("Failed %s user log in", systemUserConfig.getName()));
      });
    }
    return Future.succeededFuture(token);
  }

  @Override
  public Future<Boolean> createPubSubUser(OkapiConnectionParams params) {
    return existsPubSubUser(params)
      .compose(user -> {
        if (user != null) {
          return updateUser(user, params)
            .compose(updatedUser -> addPermissions(user.getId(), params));
        } else {
          return createUser(params)
            .compose(userId -> saveCredentials(userId, params))
            .compose(userId -> assignPermissions(userId, params));
        }
      });
  }

  private Future<User> existsPubSubUser(OkapiConnectionParams params) {
    String query = "?query=username=" + systemUserConfig.getName();
    return doRequest(params, USERS_URL + query, HttpMethod.GET, null)
      .compose(response -> {
        Promise<User> promise = Promise.promise();
        if (response.getCode() == HttpStatus.HTTP_OK.toInt()) {
          JsonObject usersCollection = response.getJson();
          JsonArray users = usersCollection.getJsonArray("users");
          if (users.size() > 0) {
            promise.complete(users.getJsonObject(0).mapTo(User.class));
          } else {
            promise.complete();
          }
        } else {
          LOGGER.error("Failed request on GET users. Received status code {}", response.getCode());
          promise.complete();
        }
        return promise.future();
      });
  }

  private Future<String> createUser(OkapiConnectionParams params) {
    final User user = createUserObject();
    final String id = user.getId();

    return doRequest(params, USERS_URL, HttpMethod.POST, encode(user))
      .compose(response -> {
        Promise<String> promise = Promise.promise();
        if (response.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Created {} user", systemUserConfig.getName());
          promise.complete(id);
        } else {
          String errorMessage = format("Failed to create %s user. Received status code %s",
            systemUserConfig.getName(), response.getCode());
          LOGGER.error(errorMessage);
          promise.fail(errorMessage);
        }
        return promise.future();
      });
  }

  private Future<User> updateUser(User existingUser, OkapiConnectionParams params) {
    if (existingUserUpToDate(existingUser)) {
      LOGGER.info("The {} user [{}] is up to date", systemUserConfig.getName(),
        existingUser.getId());
      return Future.succeededFuture(existingUser);
    }

    LOGGER.info("Have to update the {} user [{}]", systemUserConfig.getName(),
      existingUser.getId());

    final User updatedUser = populateMissingUserProperties(existingUser);
    final String url = updateUserUrl(updatedUser.getId());
    return doRequest(params, url, PUT, encode(updatedUser))
      .compose(response -> {
        Promise<User> promise = Promise.promise();
        if (response.getCode() == HTTP_NO_CONTENT.toInt()) {
          LOGGER.info("The {} user [{}] has been updated", systemUserConfig.getName(),
            updatedUser.getId());
          promise.complete(updatedUser);
        } else {
          LOGGER.error("Unable to update the {} user [{}]", systemUserConfig.getName(),
            response.getBody());
          promise.fail(format("Unable to update the %s user: %s", systemUserConfig.getName(),
            response.getBody()));
        }
        return promise.future();
      });
  }

  private Future<String> saveCredentials(String userId, OkapiConnectionParams params) {
    return Future.succeededFuture(systemUserConfig.getUserCredentialsJson())
      .compose(credentials -> {
        credentials.put("userId", userId);
        return doRequest(params, CREDENTIALS_URL, HttpMethod.POST, credentials.encode())
          .compose(response -> {
            Promise<String> promise = Promise.promise();
            if (response.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
              LOGGER.info("Saved {} user credentials", systemUserConfig.getName());
              promise.complete(userId);
            } else {
              String errorMessage = format("Failed to save %s user credentials. Received status code %s",
                systemUserConfig.getName(), response.getCode());
              LOGGER.error(errorMessage);
              promise.fail(errorMessage);
            }
            return promise.future();
          });
      });
  }

  private Future<Boolean> assignPermissions(String userId, OkapiConnectionParams params) {
    List<String> permissions = readPermissionsFromResource(PERMISSIONS_FILE_PATH);
    if (CollectionUtils.isEmpty(permissions)) {
      LOGGER.info("No permissions found to assign to {} user", systemUserConfig.getName());
      return Future.succeededFuture(false);
    }
    JsonObject requestBody = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", userId)
      .put("permissions", new JsonArray(permissions));
    return doRequest(params, PERMISSIONS_URL, HttpMethod.POST, requestBody.encode())
      .compose(response -> {
        Promise<Boolean> promise = Promise.promise();
        if (response.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Added permissions [{}] for {} user", StringUtils.join(permissions, ","),
            systemUserConfig.getName());
          promise.complete(true);
        } else {
          String errorMessage = format("Failed to add permissions for %s user. Received status code %s",
            systemUserConfig.getName(), response.getCode());
          LOGGER.error(errorMessage);
          promise.fail(errorMessage);
        }
        return promise.future();
      });
  }

  private Future<Boolean> addPermissions(String userId, OkapiConnectionParams params) {
    List<String> permissions = readPermissionsFromResource(PERMISSIONS_FILE_PATH);
    if (CollectionUtils.isEmpty(permissions)) {
      LOGGER.info("No permissions found to add for {} user", systemUserConfig.getName());
      return Future.succeededFuture(false);
    }
    List<Future<?>> futures = new ArrayList<>();
    String permUrl = PERMISSIONS_URL + "/" + userId + "/permissions?indexField=userId";
    permissions.forEach(permission -> {
      JsonObject requestBody = new JsonObject()
        .put("permissionName", permission);
      futures.add(doRequest(params, permUrl, HttpMethod.POST, requestBody.encode())
        .compose(response -> {
          Promise<Boolean> promise = Promise.promise();
          if (response.getCode() == HttpStatus.HTTP_OK.toInt()) {
            LOGGER.info("Added permission {} for {} user", permission, systemUserConfig.getName());
            promise.complete(true);
          } else {
            String errorMessage = format("Failed to add permission %s for %s user. Received status code %s",
              permission, systemUserConfig.getName(), response.getCode());
            LOGGER.error(errorMessage);
            promise.complete(false);
          }
          return promise.future();
        }));
    });
    return GenericCompositeFuture.all(futures).map(true);
  }

  private List<String> readPermissionsFromResource(String path) {
    List<String> permissions = new ArrayList<>();
    URL url = Resources.getResource(path);
    try {
      permissions = Resources.readLines(url, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.error("Error reading permissions from {}", path, e);
    }
    return permissions;
  }

  private User createUserObject() {
    final User user = new User();

    user.setId(UUID.randomUUID().toString());
    user.setActive(true);
    user.setUsername(systemUserConfig.getName());

    user.setPersonal(new User.Personal());
    user.getPersonal().setLastName(USER_LAST_NAME);

    return user;
  }

  private boolean existingUserUpToDate(User existingUser) {
    return existingUser.getPersonal() != null
      && isNotBlank(existingUser.getPersonal().getLastName());
  }

  private User populateMissingUserProperties(User existingUser) {
    existingUser.setPersonal(new User.Personal());
    existingUser.getPersonal().setLastName(USER_LAST_NAME);

    return existingUser;
  }

  private String updateUserUrl(String userId) {
    return USERS_URL + "/" + userId;
  }
}
