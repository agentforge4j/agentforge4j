// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.retry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Decorrelated-jitter backoff delay computation, shared by every retry loop in the codebase that
 * needs to space out attempts against a transient failure.
 *
 * <p>The algorithm draws the next delay uniformly from {@code [base, min(cap, 3 * lastDelay))},
 * which spreads out concurrent retriers better than plain exponential backoff while still growing
 * the delay across attempts. This class only computes the delay; callers own their own retry loop
 * (attempt counting, which failures are retryable, whether to sleep at all) since that differs by
 * caller.
 */
public final class DecorrelatedJitter {

  private DecorrelatedJitter() {
  }

  /**
   * Computes the next decorrelated-jitter delay.
   *
   * @param baseMillis      the minimum delay (and the floor of every draw); non-negative
   * @param lastDelayMillis the delay used for the previous attempt (or the base, for the first
   *                        retry); non-negative
   * @param capMillis       the maximum delay this call may return
   * @return a delay in milliseconds, at least {@code baseMillis} and at most {@code capMillis}
   */
  public static long nextDelayMillis(long baseMillis, long lastDelayMillis, long capMillis) {
    long drawn = randomBetweenBaseAndTriple(baseMillis, lastDelayMillis);
    return Math.min(capMillis, drawn);
  }

  private static long randomBetweenBaseAndTriple(long base, long lastDelay) {
    long triple = cappedMultiplyByThree(lastDelay);
    long upperInclusive = Math.max(base, triple);
    if (upperInclusive == base) {
      return base;
    }
    long hiExclusive = upperInclusive + 1;
    if (hiExclusive <= upperInclusive) {
      // Overflowed to Long.MAX_VALUE + 1: fall back to an inclusive-of-MAX_VALUE draw.
      return ThreadLocalRandom.current().nextLong(base, Long.MAX_VALUE);
    }
    return ThreadLocalRandom.current().nextLong(base, hiExclusive);
  }

  private static long cappedMultiplyByThree(long value) {
    if (value > Long.MAX_VALUE / 3) {
      return Long.MAX_VALUE;
    }
    return value * 3;
  }
}
