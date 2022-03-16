package org.folio.services.cache;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.MessagingModuleDao;
import org.folio.rest.jaxrs.model.MessagingModule;
import org.folio.services.util.ClockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.commons.collections4.IterableUtils.isEmpty;
import static org.folio.services.util.ClockUtil.*;

/**
 * In-memory storage for messaging modules
 */
@Component
public class Cache {
  private static final String MESSAGING_MODULES_CACHE_KEY = "messaging_modules";
  private static final Logger LOGGER = LogManager.getLogger();

  private AsyncLoadingCache<String, Set<MessagingModule>> loadingCache;
  @Value("${CACHE_EXPIRATION_TIMING}")
  private long cacheExpirationTiming;
  private Vertx vertx;
  private LocalDateTime lastTimeLoaded;
  private com.github.benmanes.caffeine.cache.Cache<String, String> subscriptions;
  private com.github.benmanes.caffeine.cache.Cache<String, String> tenantToken;
  private MessagingModuleDao messagingModuleDao;
  private final ReentrantLock mutex = new ReentrantLock();

  public Cache(@Autowired Vertx vertx, @Autowired MessagingModuleDao messagingModuleDao) {
    this.messagingModuleDao = messagingModuleDao;
    this.vertx = vertx;
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
           loadModulesToCache(messagingModules)
             .onComplete(ar -> {
               lastTimeLoaded = getLocalDateTime();
               promise.complete(messagingModules);
             });
         } else {
           HashSet<MessagingModule> modulesBeforeUpdate = new HashSet<>(messagingModules);
           promise.complete(modulesBeforeUpdate);
           if (mutex.tryLock() && (Duration.between(lastTimeLoaded, getLocalDateTime())
             .toMillis() >= cacheExpirationTiming)) {
             vertx.runOnContext(ignored ->
                 loadModulesToCache(messagingModules)
                   .onSuccess(ar ->
                     lastTimeLoaded = getLocalDateTime()
                     //TODO Test successful case(after some amount of time we publish the event with 201)
                     //TODO and on failure(add incorrect data to the db)
                     //TODO Question - how do we know in the tests that this piece of logic got executed and what's the result?
                   )
                   .onComplete(ar -> mutex.unlock())
                   .onFailure(ar -> {
                     LOGGER.error("Failed to load messaging modules asynchronously");
                     messagingModules.clear();
                     messagingModules.addAll(modulesBeforeUpdate);
                   })
               );
             }
           }
       } else {
         promise.fail(throwable);
       }
     });
   return promise.future();
 }


  private Future<Boolean> loadModulesToCache(Set<MessagingModule> messagingModules) {
    return messagingModuleDao.getAll()
      .map(messagingModules::addAll);
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
