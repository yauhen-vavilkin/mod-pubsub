package org.folio.services.impl;

import static io.vertx.core.http.HttpMethod.PUT;
import static io.vertx.core.json.Json.encode;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TOKEN_HEADER;
import static org.folio.rest.util.RestUtil.doRequest;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.primitives.Booleans;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.HttpStatus;
import org.folio.dao.PubSubUserDao;
import org.folio.representation.User;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.services.SecurityManager;
import org.folio.services.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.io.Resources;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@Component
public class SecurityManagerImpl implements SecurityManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityManagerImpl.class);

  private static final String LOGIN_URL = "/authn/login";
  private static final String USERS_URL = "/users";
  private static final String CREDENTIALS_URL = "/authn/credentials";
  private static final String PERMISSIONS_URL = "/perms/users";
  private static final String PERMISSIONS_FILE_PATH = "permissions/pubsub-user-permissions.csv";
  private static final String PUB_SUB_USERNAME = "pub-sub";
  private static final String USER_LAST_NAME = "System";

  private final PubSubUserDao pubSubUserDao;
  private final Vertx vertx;
  private final Cache cache;

  public SecurityManagerImpl(@Autowired PubSubUserDao pubSubUserDao, @Autowired Vertx vertx, @Autowired Cache cache) {
    this.pubSubUserDao = pubSubUserDao;
    this.vertx = vertx;
    this.cache = cache;
  }

  @Override
  public Future<Boolean> loginPubSubUser(OkapiConnectionParams params) {
    params.setToken(EMPTY);

    String token = cache.getToken(params.getTenantId());
    if (!StringUtils.isEmpty(token)) {
      return Future.succeededFuture(true);
    }

    return pubSubUserDao.getPubSubUserCredentials(params.getTenantId())
      .compose(userCredentials -> doRequest(userCredentials.encode(), LOGIN_URL, HttpMethod.POST, params))
      .compose(response -> {
        if (response.statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Logged in pub-sub user");
          cache.addToken(params.getTenantId(), response.getHeader(OKAPI_TOKEN_HEADER));
          return Future.succeededFuture(true);
        }
        LOGGER.error("pub-sub user was not logged in, received status {}", response.statusCode());
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
        return Future.failedFuture("Failed pub-sub user log in");
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
    String query = "?query=username=" + PUB_SUB_USERNAME;
    return doRequest(null, USERS_URL + query, HttpMethod.GET, params)
      .compose(response -> {
        Promise<User> promise = Promise.promise();
        if (response.statusCode() == HttpStatus.HTTP_OK.toInt()) {
          JsonObject usersCollection = response.bodyAsJsonObject();
          JsonArray users = usersCollection.getJsonArray("users");
          if (users.size() > 0) {
            promise.complete(users.getJsonObject(0).mapTo(User.class));
          } else {
            promise.complete();
          }
        } else {
          LOGGER.error("Failed request on GET users. Received status code {}", response.statusCode());
          promise.complete();
        }
        return promise.future();
      });
  }

  private Future<String> createUser(OkapiConnectionParams params) {
    final User user = createUserObject();
    final String id = user.getId();

    return doRequest(encode(user), USERS_URL, HttpMethod.POST, params)
      .compose(response -> {
        Promise<String> promise = Promise.promise();
        if (response.statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Created pub-sub user");
          promise.complete(id);
        } else {
          String errorMessage = format("Failed to create pub-sub user. Received status code %s", response.statusCode());
          LOGGER.error(errorMessage);
          promise.fail(errorMessage);
        }
        return promise.future();
      });
  }

  private Future<User> updateUser(User existingUser, OkapiConnectionParams params) {
    if (existingUserUpToDate(existingUser)) {
      LOGGER.info("The pub-sub user [{}] is up to date", existingUser.getId());
      return Future.succeededFuture(existingUser);
    }

    LOGGER.info("Have to update the pub-sub user [{}]", existingUser.getId());

    final User updatedUser = populateMissingUserProperties(existingUser);
    final String url = updateUserUrl(updatedUser.getId());
    return doRequest(encode(updatedUser), url, PUT, params)
      .compose(response -> {
        Promise<User> promise = Promise.promise();
        if (response.statusCode() == HTTP_NO_CONTENT.toInt()) {
          LOGGER.info("The pub-sub user [{}] has been updated", updatedUser.getId());
          promise.complete(updatedUser);
        } else {
          LOGGER.error("Unable to update the pub-sub user [{}]", response.bodyAsString());
          promise.fail("Unable to update the pub-sub user: " + response.bodyAsString());
        }
        return promise.future();
      });
  }

  private Future<String> saveCredentials(String userId, OkapiConnectionParams params) {
    return pubSubUserDao.getPubSubUserCredentials(params.getTenantId())
      .compose(credentials -> {
        credentials.put("userId", userId);
        return doRequest(credentials.encode(), CREDENTIALS_URL, HttpMethod.POST, params)
          .compose(response -> {
            Promise<String> promise = Promise.promise();
            if (response.statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
              LOGGER.info("Saved pub-sub user credentials");
              promise.complete(userId);
            } else {
              String errorMessage = format("Failed to save pub-sub user credentials. Received status code %s", response.statusCode());
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
      LOGGER.info("No permissions found to assign to pub-sub user");
      return Future.succeededFuture(false);
    }
    JsonObject requestBody = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("userId", userId)
      .put("permissions", new JsonArray(permissions));
    return doRequest(requestBody.encode(), PERMISSIONS_URL, HttpMethod.POST, params)
      .compose(response -> {
        Promise<Boolean> promise = Promise.promise();
        if (response.statusCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("Added permissions [{}] for pub-sub user", StringUtils.join(permissions, ","));
          promise.complete(true);
        } else {
          String errorMessage = format("Failed to add permissions for pub-sub user. Received status code %s", response.statusCode());
          LOGGER.error(errorMessage);
          promise.complete(false);
        }
        return promise.future();
      });
  }

  private Future<Boolean> addPermissions(String userId, OkapiConnectionParams params) {
    List<String> permissions = readPermissionsFromResource(PERMISSIONS_FILE_PATH);
    if (CollectionUtils.isEmpty(permissions)) {
      LOGGER.info("No permissions found to add for pub-sub user");
      return Future.succeededFuture(false);
    }
    List<Future> futures = new ArrayList<>();
    String permUrl = PERMISSIONS_URL + "/" + userId + "/permissions?indexField=userId";
    permissions.forEach(permission -> {
      JsonObject requestBody = new JsonObject()
        .put("permissionName", permission);
      futures.add(doRequest(requestBody.encode(), permUrl, HttpMethod.POST, params)
        .compose(response -> {
          Promise<Boolean> promise = Promise.promise();
          if (response.statusCode() == HttpStatus.HTTP_OK.toInt()) {
            LOGGER.info("Added permission {} for pub-sub user", permission);
            promise.complete(true);
          } else {
            String errorMessage = format("Failed to add permission %s for pub-sub user. Received status code %s", permission, response.statusCode());
            LOGGER.error(errorMessage);
            promise.complete(false);
          }
          return promise.future();
        }));
    });
    return CompositeFuture.all(futures).map(true);
  }

  private List<String> readPermissionsFromResource(String path) {
    List<String> permissions = new ArrayList<>();
    URL url = Resources.getResource(path);
    try {
      permissions = Resources.readLines(url, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.error("Error reading permissions from {}", e, path);
    }
    return permissions;
  }

  private User createUserObject() {
    final User user = new User();

    user.setId(UUID.randomUUID().toString());
    user.setActive(true);
    user.setUsername(PUB_SUB_USERNAME);

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
