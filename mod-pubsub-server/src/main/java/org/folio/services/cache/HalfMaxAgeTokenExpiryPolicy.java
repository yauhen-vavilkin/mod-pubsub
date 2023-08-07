package org.folio.services.cache;

import java.util.concurrent.TimeUnit;

import org.checkerframework.checker.index.qual.NonNegative;
import org.folio.rest.util.ExpiryAwareToken;

import com.github.benmanes.caffeine.cache.Expiry;

public class HalfMaxAgeTokenExpiryPolicy implements Expiry<String, ExpiryAwareToken> {
  @Override
  public long expireAfterCreate(String key, ExpiryAwareToken token, long currentTime) {
    return TimeUnit.SECONDS.toNanos(token.getMaxAge() / 2);
  }

  @Override
  public long expireAfterUpdate(String key, ExpiryAwareToken token, long currentTime,
    @NonNegative long currentDuration) {

    return TimeUnit.SECONDS.toNanos(token.getMaxAge() / 2);
  }

  @Override
  public long expireAfterRead(String key, ExpiryAwareToken token, long currentTime,
    @NonNegative long currentDuration) {

    return currentDuration;
  }
}
