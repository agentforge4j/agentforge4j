// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionTest {

  @Test
  void allowCarriesNoReason() {
    assertThat(Decision.allow().allowed()).isTrue();
    assertThat(Decision.allow().reason()).isNull();
  }

  @Test
  void allowingConstructorNullsOutAnyGivenReason() {
    Decision decision = new Decision(true, "ignored");
    assertThat(decision.reason()).isNull();
  }

  @Test
  void denyRequiresANonBlankReason() {
    assertThat(Decision.deny("nope").reason()).isEqualTo("nope");
  }

  @Test
  void denyingConstructorRejectsNullReason() {
    assertThatThrownBy(() -> new Decision(false, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void denyingConstructorRejectsBlankReason() {
    assertThatThrownBy(() -> new Decision(false, "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
