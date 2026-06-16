// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.retry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

  @Test
  void none_factory_disallows_retry_and_zero_attempts() {
    RetryPolicy p = RetryPolicy.none();
    assertThat(p.allowRetry()).isFalse();
    assertThat(p.maxAttempts()).isZero();
  }

  @Test
  void simple_enables_retry_with_given_cap() {
    RetryPolicy p = RetryPolicy.simple(3);
    assertThat(p.allowRetry()).isTrue();
    assertThat(p.maxAttempts()).isEqualTo(3);
  }

  @Test
  void rejects_zero_max_attempts_when_retry_enabled() {
    assertThatThrownBy(() -> RetryPolicy.simple(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts");
  }

  @Test
  void rejects_negative_max_attempts() {
    assertThatThrownBy(() -> new RetryPolicy(true, false, false, false, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts");
  }
}
