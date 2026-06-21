// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EgressCheckResultTest {

  @Test
  void allowedResultCarriesNoReason() {
    EgressCheckResult result = EgressCheckResult.allow();

    assertThat(result.allowed()).isTrue();
    assertThat(result.reason()).isNull();
  }

  @Test
  void deniedResultCarriesItsReason() {
    EgressCheckResult result = EgressCheckResult.deny("host resolves to a non-public address");

    assertThat(result.allowed()).isFalse();
    assertThat(result.reason()).isEqualTo("host resolves to a non-public address");
  }

  @Test
  void rejectsAnAllowedResultWithAMisleadingReason() {
    assertThatThrownBy(() -> new EgressCheckResult(true, "denied")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not carry a reason");
  }

  @Test
  void rejectsADeniedResultWithABlankReason() {
    assertThatThrownBy(() -> new EgressCheckResult(false, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EgressCheckResult(false, "   ")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-blank reason");
  }
}
