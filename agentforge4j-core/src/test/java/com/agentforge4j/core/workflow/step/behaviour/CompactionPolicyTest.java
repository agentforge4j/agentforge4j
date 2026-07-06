// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactionPolicyTest {

  @Test
  void retainsThresholds() {
    CompactionPolicy policy = new CompactionPolicy(500, 2);

    assertThat(policy.minSourceUnits()).isEqualTo(500);
    assertThat(policy.minDownstreamReuse()).isEqualTo(2);
  }

  @Test
  void allowsZeroReuseToCompactRegardless() {
    assertThat(new CompactionPolicy(0, 0).minDownstreamReuse()).isZero();
  }

  @Test
  void rejectsNegativeThresholds() {
    assertThatThrownBy(() -> new CompactionPolicy(-1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CompactionPolicy(0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
