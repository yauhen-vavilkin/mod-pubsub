package org.folio.services.cache;

import static org.apache.commons.collections4.IterableUtils.isEmpty;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.index.qual.NonNegative;
import org.folio.dao.MessagingModuleDao;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.rest.util.ExpiryAwareToken;
import org.folio.rest.util.OkapiConnectionParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.Setter;

/**
 * In-memory storage for messaging modules
 */
@Component
public class Cache {
  private static final String MESSAGING_MODULES_CACHE_KEY = "messaging_modules";
  private static final Logger LOGGER = LogManager.getLogger();

  private AsyncLoadingCache<String, Set<MessagingModule>> loadingCache;
  private com.github.benmanes.caffeine.cache.Cache<String, String> subscriptions;
  private com.github.benmanes.caffeine.cache.Cache<String, ExpiryAwareToken> tenantAccessToken;
  private com.github.benmanes.caffeine.cache.Cache<String, ExpiryAwareToken> tenantRefreshToken;
  private com.github.benmanes.caffeine.cache.Cache<String, OkapiConnectionParams> knownOkapiParams;
  private MessagingModuleDao messagingModuleDao;

  @Setter
  private Consumer<ExpiryAwareToken> tokensRefreshFunction;

  public Cache(@Autowired Vertx vertx, @Autowired MessagingModuleDao messagingModuleDao) {
    this.messagingModuleDao = messagingModuleDao;
    this.loadingCache = Caffeine.newBuilder()
      .executor(serviceExecutor -> vertx.runOnContext(ar -> serviceExecutor.run()))
      .buildAsync(k -> new HashSet<>());
    this.subscriptions = Caffeine.newBuilder().build();
    this.tenantAccessToken = Caffeine.newBuilder()
      .scheduler(Scheduler.systemScheduler())
      .expireAfter(new HalfMaxAgeTokenExpiryPolicy())
      .removalListener((tenant, token, cause) -> {
        LOGGER.info("Cache:: Access token removed for tenant {}", tenant);
        if (cause == RemovalCause.EXPIRED) {
          LOGGER.info("Cache:: Access token expired for tenant {}", tenant);
          tokensRefreshFunction.accept(token);
        }
      })
      .build();
    this.tenantRefreshToken = Caffeine.newBuilder()
      .scheduler(Scheduler.systemScheduler())
      .expireAfter(new HalfMaxAgeTokenExpiryPolicy())
      .removalListener((tenant, token, cause) -> {
        LOGGER.info("Cache:: Refresh token removed for tenant {}", tenant);
        if (cause == RemovalCause.EXPIRED) {
          LOGGER.info("Cache:: Refresh token expired for tenant {}", tenant);
          tokensRefreshFunction.accept(token);
        }
      })
      .build();
    this.knownOkapiParams = Caffeine.newBuilder().build();
  }

  public Future<Set<MessagingModule>> getMessagingModules() {
    LOGGER.info("getMessagingModules() called");
    Promise<Set<MessagingModule>> promise = Promise.promise();
    loadingCache
      .get(MESSAGING_MODULES_CACHE_KEY)
      .whenComplete((messagingModules, throwable) -> {
        LOGGER.info("messaging modules retrieved");
        if (throwable == null) {
          LOGGER.info("throwable is null");
          if (isEmpty(messagingModules)) {
            LOGGER.info("messagingModules is empty, fetching from DB");
            messagingModuleDao.getAll()
              .map(messagingModules::addAll)
              .onComplete(ar -> {
                LOGGER.info("messaging modules fetched:\n{}", stringify(messagingModules));
                promise.complete(messagingModules);
              });
          } else {
            LOGGER.info("cached messaging modules:\n{}", stringify(messagingModules));
            promise.complete(messagingModules);
          }
        } else {
          LOGGER.error("failed to retrieve messaging modules", throwable);
          promise.fail(throwable);
        }
      });
    return promise.future();
  }

  public boolean containsSubscription(String topic) {
    return subscriptions.getIfPresent(topic) != null;
  }

  public void addSubscription(String topic) {
    subscriptions.put(topic, topic);
  }

  public void setAccessToken(String tenant, ExpiryAwareToken accessToken) {
    LOGGER.info("setAccessToken:: updating access token cache for tenant {}", tenant);
    tenantAccessToken.invalidate(tenant);
    tenantAccessToken.put(tenant, accessToken);
  }

  public void setRefreshToken(String tenant, ExpiryAwareToken refreshToken) {
    LOGGER.info("setRefreshToken:: updating refresh token cache for tenant {}", tenant);
    tenantRefreshToken.invalidate(tenant);
    tenantRefreshToken.put(tenant, refreshToken);
  }

  public ExpiryAwareToken getAccessExpiryAwareToken(String tenant) {
    return tenantAccessToken.getIfPresent(tenant);
  }

  public String getAccessToken(String tenant) {
    ExpiryAwareToken expiryAwareToken = tenantAccessToken.getIfPresent(tenant);
    if (expiryAwareToken != null) {
      return expiryAwareToken.getToken();
    }
    return null;
  }

  public ExpiryAwareToken getRefreshExpiryAwareToken(String tenant) {
    return tenantRefreshToken.getIfPresent(tenant);
  }

  public String getRefreshToken(String tenant) {
    ExpiryAwareToken expiryAwareToken = tenantRefreshToken.getIfPresent(tenant);
    if (expiryAwareToken != null) {
      return expiryAwareToken.getToken();
    }
    return null;
  }

  public void invalidate() {
    loadingCache.synchronous().invalidateAll();
  }

  public void invalidateAccessToken(String tenantId) {
    tenantAccessToken.invalidate(tenantId);
  }

  public void invalidateRefreshToken(String tenantId) {
    tenantRefreshToken.invalidate(tenantId);
  }

  public OkapiConnectionParams getKnownOkapiParams(String tenant) {
    return knownOkapiParams.getIfPresent(tenant);
  }

  public void setKnownOkapiParams(String tenant, OkapiConnectionParams params) {
    knownOkapiParams.put(tenant, params);
  }

  private static String stringify(Collection<MessagingModule> modules) {
    return modules.stream()
      .map(module -> String.format("[%s] [%s] [%s] [%s]",
        module.getTenantId(), module.getModuleId(), module.getModuleRole(), module.getEventType()))
      .collect(Collectors.joining("\n"));
  }
}
