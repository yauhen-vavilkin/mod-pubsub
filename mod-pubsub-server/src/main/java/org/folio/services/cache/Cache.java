package org.folio.services.cache;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.folio.dao.MessagingModuleDao;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static org.apache.commons.collections4.IterableUtils.isEmpty;

/**
 * In-memory storage for messaging modules
 */
@Component
public class Cache {
  private static final String MESSAGING_MODULES_CACHE_KEY = "messaging_modules";

  private AsyncLoadingCache<String, Set<MessagingModule>> loadingCache;
  private com.github.benmanes.caffeine.cache.Cache<String, String> subscriptions;
  private com.github.benmanes.caffeine.cache.Cache<String, String> tenantToken;
  private MessagingModuleDao messagingModuleDao;

  public Cache(@Autowired Vertx vertx, @Autowired MessagingModuleDao messagingModuleDao) {
    this.messagingModuleDao = messagingModuleDao;
    this.loadingCache = Caffeine.newBuilder()
      .executor(serviceExecutor -> vertx.runOnContext(ar -> serviceExecutor.run()))
      .buildAsync(k -> new HashSet<>());
    this.subscriptions = Caffeine.newBuilder().build();
    this.tenantToken = Caffeine.newBuilder().build();
  }

  public Future<Set<MessagingModule>> getMessagingModules() {
    Promise<Set<MessagingModule>> promise = Promise.promise();
    loadingCache
      .get(MESSAGING_MODULES_CACHE_KEY)
      .whenComplete((messagingModules, throwable) -> {
        if (throwable == null) {
          if (isEmpty(messagingModules)) {
            messagingModuleDao.getAll()
              .map(messagingModules::addAll)
              .onComplete(ar -> promise.complete(messagingModules));
          } else {
            promise.complete(messagingModules);
          }
        } else {
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

  public void addToken(String tenant, String token) {
    tenantToken.invalidate(tenant);
    tenantToken.put(tenant, token);
  }

  public String getToken(String tenant) {
    return tenantToken.getIfPresent(tenant);
  }

  public void invalidate() {
    loadingCache.synchronous().invalidateAll();
  }

}
