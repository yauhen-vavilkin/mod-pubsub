package org.folio.kafka.cache;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.KafkaConfig;

import io.kcache.Cache;
import io.kcache.KafkaCache;
import io.kcache.KeyValueIterator;
import lombok.Builder;

/**
 * Cache in Kafka for processing events.
 */
public class KafkaInternalCache {

  private static final Logger LOGGER = LogManager.getLogger(KafkaInternalCache.class);

  private final KafkaConfig kafkaConfig;
  private Cache<String, String> kafkaCache;

  @Builder
  private KafkaInternalCache(KafkaConfig kafkaConfig) {
    this.kafkaConfig = kafkaConfig;
  }

  /**
   * Initialize cache for Kafka with specific properties in KafkaConfig.
   */
  public void initKafkaCache() {
    Cache<String, String> cache = new KafkaCache<>(
      kafkaConfig.getCacheConfig(),
      Serdes.String(),
      Serdes.String()
    );
    this.kafkaCache = cache;
    cache.init();
  }


  /**
   * Put to the cache an element with specific key. Tha value - current time. Example : 2021-03-17T14:58:05.725953.
   * Stored as Strings.
   * @param key - key for the event. Mostly - uuid.
   */
  public void putToCache(String key) {
    String currentTime = LocalDateTime.now().toString();
    kafkaCache.put(key, currentTime);
  }

  /**
   * Check if element in cache exists by specified key.
   * @param key - element`s key.
   * @return true - if cache contains element by this key. false - if not.
   */
  public boolean containsByKey(String key) {
    String value = kafkaCache.get(key);
    return value != null;
  }

  /**
   * Clean Kafka cache from outdated elements.
   * Outdated element - element difference between its value and current time is more or equal to the eventTimeoutHours.
   * @param eventTimeoutHours - timeout for event in hours.
   */
  public void cleanupEvents(int eventTimeoutHours) {
    List<String> outdatedEvents = new ArrayList<>();

    LocalDateTime currentTime = LocalDateTime.now();
    KeyValueIterator<String, String> events = kafkaCache.all();
    events.forEachRemaining(currentEvent -> {
      LocalDateTime eventTime = LocalDateTime.parse(currentEvent.value);
      long hoursBetween = ChronoUnit.HOURS.between(eventTime, currentTime);
      if (hoursBetween >= eventTimeoutHours) {
        outdatedEvents.add(currentEvent.key);
      }
    });
    if(!outdatedEvents.isEmpty()) {
      LOGGER.info("Clearing cache from outdated events...");
    }

    outdatedEvents.forEach(outdatedEvent -> kafkaCache.remove(outdatedEvent));
  }
}
