package org.folio.services.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A clock manager for safely getting and setting the time.
 * <p>
 * Provides management of the clock that is then exposed and used as the
 * default clock for all methods within this utility.
 * <p>
 * Use these methods rather than using the now() methods for any given date and
 * time related class.
 * Failure to do so may result in the inability to properly perform tests.
 */
public class ClockUtil {
  private static Clock clock = Clock.systemUTC();

  private ClockUtil() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  /**
   * Set the clock assigned to the clock manager to a given clock.
   */
  public static void setClock(Clock clock) {
    if (clock == null) {
      throw new IllegalArgumentException("clock cannot be null");
    }

    ClockUtil.clock = clock;
  }

  public static Clock getClock() {
    return clock;
  }

  /**
   * Get the current system time according to the clock manager.
   *
   * @return
   *   A LocalDateTime as if now() is called.
   *   Time is truncated to milliseconds.
   */
  public static LocalDateTime getLocalDateTime() {
    return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }
}

