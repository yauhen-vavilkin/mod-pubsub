package org.folio.services.impl;

import static io.vertx.core.http.HttpMethod.PUT;
import static io.vertx.core.json.Json.encode;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.rest.util.RestUtil.doRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.HttpStatus;
import org.folio.config.user.SystemUserConfig;
import org.folio.representation.User;
import org.folio.rest.util.ExpiryAwareToken;
import org.folio.rest.util.OkapiConnectionParams;
import org.folio.rest.util.RestUtil;
import org.folio.services.SecurityManager;
import org.folio.services.cache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.io.Resources;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Component
public class SecurityManagerImpl implements SecurityManager {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final String LOGIN_WITH_EXPIRY_URL = "/authn/login-with-expiry";
  private static final String USERS_URL = "/users";
  private static final String CREDENTIALS_URL = "/authn/credentials";
  private static final String PERMISSIONS_URL = "/perms/users";
  private static final String PERMISSIONS_FILE_PATH = "permissions/pubsub-user-permissions.csv";
  private static final String USER_LAST_NAME = "System";
  private static final List<String> PERMISSIONS = readPermissionsFromResource(PERMISSIONS_FILE_PATH);
  private static final String ACCESS_TOKEN_NAME = "folioAccessToken";
  private static final String REFRESH_TOKEN_NAME = "folioRefreshToken";
  private static final String SYSTEM_USER_TYPE = "system";

  private final Cache cache;
  private final SystemUserConfig systemUserConfig;

  public SecurityManagerImpl(@Autowired Cache cache,
    @Autowired SystemUserConfig systemUserConfig) {

    this.cache = cache;
    this.systemUserConfig = systemUserConfig;

    if (this.cache != null) {
      // It's recommended that BE modules don't use /authn/refresh API because they always have
      // credentials
      this.cache.setTokensRefreshFunction(this::logInWithExpiry);
    } else {
      LOGGER.warn("SecurityManagerImpl:: Cache is null, failed to set refresh function");
    }
  }

  @Override
  public Future<String> getAccessToken(OkapiConnectionParams params) {
    params.setToken(EMPTY);
    final String tenantId = params.getTenantId();

    String cachedAccessToken = cache.getAccessToken(tenantId);
    if (!StringUtils.isEmpty(cachedAccessToken)) {
      LOGGER.debug("getAccessToken:: Using cached access token for tenant {}",
        params.getTenantId());
      return Future.succeededFuture(cachedAccessToken);
    }

    return logInWithExpiry(params)
      .map(v -> cache.getAccessToken(tenantId));
  }

  public Future<Void> logInWithExpiry(OkapiConnectionParams params) {
    final String tenantId = params.getTenantId();
    LOGGER.info("logInWithExpiry:: Logging in, tenantId={}", tenantId);

    return Future.succeededFuture(systemUserConfig.getUserCredentialsJson())
      .compose(userCredentials -> doRequest(params, LOGIN_WITH_EXPIRY_URL,
        HttpMethod.POST, userCredentials.encode()))
      .compose(response -> {
        if (response.getCode() == HttpStatus.HTTP_CREATED.toInt()) {
          LOGGER.info("logInWithExpiry:: Logged in {} user", systemUserConfig.getName());

          ExpiryAwareToken accessToken = fetchTokenFromCookies(response, ACCESS_TOKEN_NAME, params);
          if (accessToken == null) {
            return tokenFetchFailure(ACCESS_TOKEN_NAME);
          }

          ExpiryAwareToken refreshToken = fetchTokenFromCookies(response, REFRESH_TOKEN_NAME,
            params);
          if (refreshToken == null) {
            return tokenFetchFailure(REFRESH_TOKEN_NAME);
          }

          LOGGER.info("logInWithExpiry:: Parsed 'access' and 'refresh' tokens, caching");
          cache.setAccessToken(tenantId, accessToken);
          cache.setRefreshToken(tenantId, refreshToken);
          return Future.succeededFuture();
        }
        String message = String.format("%s user was not logged in, received status %d",
          systemUserConfig.getName(), response.getCode());
        LOGGER.warn("logInWithExpiry:: {}", message);
        return Future.failedFuture(message);
      });
  }

  public Future<Void> logInWithExpiry(ExpiryAwareToken token) {
    return logInWithExpiry(token.getOkapiParams());
  }

  private ExpiryAwareToken fetchTokenFromCookies(RestUtil.WrappedResponse response,
    String tokenName, OkapiConnectionParams params) {

    String tokenCookie = response.getResponse().cookies().stream()
      .filter(t -> t.contains(tokenName))
      .findFirst()
      .orElse(null);

    if (tokenCookie != null) {
      String token = Arrays.stream(tokenCookie.split(";"))
        .map(String::trim)
        .map(param -> param.split("="))
        .filter(keyValuePair -> tokenName.equals(keyValuePair[0]))
        .map(keyValuePair -> keyValuePair[1])
        .findFirst()
        .orElse(null);

      long maxAge = Arrays.stream(tokenCookie.split(";"))
        .map(String::trim)
        .map(param -> param.split("="))
        .filter(keyValuePair -> "Max-Age".equals(keyValuePair[0]))
        .map(keyValuePair -> keyValuePair[1])
        .mapToLong(Long::parseLong)
        .findFirst()
        .orElse(0L);

      return new ExpiryAwareToken(token, maxAge, params);
    }

    return null;
  }

  private Future<Void> tokenFetchFailure(String tokenName) {
    String logMessage = String.format("Failed to fetch %s from cookies", tokenName);
    LOGGER.warn("tokenFetchFailure:: {}", logMessage);
    return Future.failedFuture(logMessage);
  }

  @Override
  public Future<Void> createPubSubUser(OkapiConnectionParams params) {
    if (!systemUserConfig.isCreateUser()){
      return Future.succeededFuture();
    }

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
    cache.invalidateAccessToken(tenantId);
    cache.invalidateRefreshToken(tenantId);
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
    user.setType(SYSTEM_USER_TYPE);

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
