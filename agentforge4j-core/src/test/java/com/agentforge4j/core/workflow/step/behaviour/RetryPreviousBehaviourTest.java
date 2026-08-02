// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPreviousBehaviourTest {

  @Test
  void rejects_null_fallback() {
    assertThatThrownBy(() -> new RetryPreviousBehaviour("s1", RetryMode.SINGLE_STEP, 3, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fallback must not be null");
  }
}
