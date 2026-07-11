// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.retry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecorrelatedJitterTest {

  @Test
  void returnsExactlyBaseWhenTripleOfLastDelayDoesNotExceedBase() {
    long delay = DecorrelatedJitter.nextDelayMillis(100L, 0L, 10_000L);

    assertThat(delay).isEqualTo(100L);
  }

  @Test
  void everyDrawStaysWithinBaseAndTripleOfLastDelay() {
    for (int i = 0; i < 200; i++) {
      long delay = DecorrelatedJitter.nextDelayMillis(50L, 400L, 2_000L);

      assertThat(delay).isBetween(50L, 1_200L);
    }
  }

  @Test
  void drawNeverExceedsTheCapEvenWhenTripleWouldExceedIt() {
    for (int i = 0; i < 200; i++) {
      long delay = DecorrelatedJitter.nextDelayMillis(100L, 10_000L, 500L);

      assertThat(delay).isBetween(100L, 500L);
    }
  }

  @Test
  void zeroBaseAndZeroLastDelayStaysAtZero() {
    long delay = DecorrelatedJitter.nextDelayMillis(0L, 0L, 1_000L);

    assertThat(delay).isEqualTo(0L);
  }

  @Test
  void overflowingTripleFallsBackWithoutThrowing() {
    long delay = DecorrelatedJitter.nextDelayMillis(1L, Long.MAX_VALUE, Long.MAX_VALUE);

    assertThat(delay).isGreaterThanOrEqualTo(1L).isLessThan(Long.MAX_VALUE);
  }
}
