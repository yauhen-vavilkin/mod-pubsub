package org.folio.rest.util;

import static org.folio.services.util.ClockUtil.getClock;
import static org.folio.services.util.ClockUtil.setClock;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class ClockUtil {

  public static void runWithFrozenClock(Runnable runnable, ZonedDateTime mockSystemTime) {
    final Clock original = getClock();

    try {
      setClock(Clock.fixed(mockSystemTime.toInstant(), ZoneOffset.UTC));

      runnable.run();
    } finally {
      setClock(original);
    }
  }
}
