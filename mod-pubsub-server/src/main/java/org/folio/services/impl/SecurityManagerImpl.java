package org.folio.services.impl;

import com.google.common.io.Resources;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.config.user.SystemUserConfig;
import org.folio.representation.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.SecurityManager;
import org.folio.services.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
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
  private static final List<String> PERMISSIONS = readPermissionsFromResource(PERMISSIONS_FILE_PATH);

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
  public Future<String> getJWTToken(OkapiConnectionParams params) {
    params.setToken(EMPTY);

    String cachedToken = cache.getToken(params.getTenantId());
    if (!StringUtils.isEmpty(cachedToken)) {
      return Future.succeededFuture(cachedToken);
    }

    return Future.succeededFuture(systemUserConfig.getUserCredentialsJson())
      .compose(userCredentials -> doRequest(params, LOGIN_URL, HttpMethod.POST, userCredentials.encode()))
      .compose(response -> {
        if (response.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Logged in {} user", systemUserConfig.getName());
          String token = response.getResponse().getHeader(OKAPI_TOKEN_HEADER);
          cache.addToken(params.getTenantId(), token);
          return Future.succeededFuture(token);
        }
        String message = String.format("%s user was not logged in, received status %d",
            systemUserConfig.getName(), response.getCode());
        LOGGER.error(message);
        return Future.failedFuture(message);
      });
  }

  @Override
  public Future<Void> createPubSubUser(OkapiConnectionParams params) {
    return existsPubSubUser(params)
      .compose(user -> {
        if (user != null) {
          return updateUser(user, params)
            .compose(updatedUser -> establishPermissionsUser(user.getId(), params));
        } else {
          return createUser(params)
            .compose(userId -> saveCredentials(userId, params))
            .compose(userId -> establishPermissionsUser(userId, params));
        }
      });
  }

  @Override
  public void invalidateToken(String tenantId) {
    cache.invalidateToken(tenantId);
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

  /**
   * Create or Update permission user.
   * @param userId user ID as known from mod-users
   * @param params connection params.
   * @return async result.
   */
  private Future<Void> establishPermissionsUser(String userId, OkapiConnectionParams params) {
    // have to do a GET first as PUT does not allow indexField=userId.. If that was available, the
    // GET would not be necessary.
    return doRequest(params, PERMISSIONS_URL + "/" + userId + "?indexField=userId", HttpMethod.GET,
      null).compose(res1 -> {
      if (res1.getCode() == HttpStatus.HTTP_OK.toInt()) {
        if (res1.getJson().getJsonArray("permissions").equals(new JsonArray(PERMISSIONS))) {
          return Future.succeededFuture();
        }
        JsonObject requestBody = res1.getJson()
          .put("permissions", new JsonArray(PERMISSIONS));
        return doRequest(params, PERMISSIONS_URL + "/" + requestBody.getString("id"), HttpMethod.PUT,
          requestBody.encode())
          .compose(res2 -> {
            if (res2.getCode() == HttpStatus.HTTP_OK.toInt()) {
              LOGGER.info("Updated user {} with permissions [{}]", systemUserConfig.getName(),
                StringUtils.join(PERMISSIONS, ","));
              return Future.succeededFuture();
            }
            String errorMessage = format("Failed to update permissions %s for %s user. Received status code %s: %s",
              StringUtils.join(PERMISSIONS, ","), systemUserConfig.getName(), res2.getCode(),
              res2.getBody());
            LOGGER.error(errorMessage);
            return Future.failedFuture(errorMessage);
          });
      } else if (res1.getCode() == HttpStatus.HTTP_NOT_FOUND.toInt()) {
        JsonObject requestBody = new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("userId", userId)
          .put("permissions", new JsonArray(PERMISSIONS));
        return doRequest(params, PERMISSIONS_URL, HttpMethod.POST, requestBody.encode())
          .compose(res2 -> {
            if (res2.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
              LOGGER.info("Created user {} with permissions [{}]", systemUserConfig.getName(),
                StringUtils.join(PERMISSIONS, ","));
              return Future.succeededFuture();
            }
            String errorMessage = format("Failed to add permissions %s for %s user. Received status code %s: %s",
              StringUtils.join(PERMISSIONS, ","), systemUserConfig.getName(), res2.getCode(),
              res2.getBody());
            LOGGER.error(errorMessage);
            return Future.failedFuture(errorMessage);
          });
      }
      String errorMessage = format("Failed to get permissions for %s user. Received status code %s: %s",
        systemUserConfig.getName(), res1.getCode(), res1.getBody());
      LOGGER.error(errorMessage);
      return Future.failedFuture(errorMessage);
    });
  }

  static List<String> readPermissionsFromResource(String path) {
    try {
      URL url = Resources.getResource(path);
      List<String> permissions = Resources.readLines(url, StandardCharsets.UTF_8);
      if (CollectionUtils.isEmpty(permissions)) {
        throw new NoSuchElementException("No permission found in " + path);
      }
      return permissions;
    } catch (IOException e) {
      // can never happen because Resources.getResource throws IllegalArgumentException if not exists
      throw new UncheckedIOException("Error reading permissions from " + path, e);
    }
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
