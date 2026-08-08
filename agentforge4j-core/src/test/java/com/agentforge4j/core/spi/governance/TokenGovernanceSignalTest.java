// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.governance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenGovernanceSignalTest {

  @Test
  void buildsWithNullableAgentId() {
    TokenGovernanceSignal signal = new TokenGovernanceSignal(WasteSignalKind.OVERBROAD_CONTEXT, "s1",
        null, "detail");

    assertThat(signal.agentId()).isNull();
    assertThat(signal.stepId()).isEqualTo("s1");
  }

  @Test
  void rejectsNullKindOrBlankStepIdOrDetail() {
    assertThatThrownBy(() -> new TokenGovernanceSignal(null, "s1", "a1", "detail"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
        () -> new TokenGovernanceSignal(WasteSignalKind.DUPLICATE_INVOCATION, " ", "a1", "detail"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
        () -> new TokenGovernanceSignal(WasteSignalKind.DUPLICATE_INVOCATION, "s1", "a1", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void noOpPolicyAcceptsAnySignalWithoutThrowing() {
    TokenGovernanceSignal signal = new TokenGovernanceSignal(WasteSignalKind.DUPLICATE_INVOCATION,
        "s1", "a1", "detail");

    assertThatCode(() -> WasteSignalPolicy.NO_OP.onSignal(signal)).doesNotThrowAnyException();
  }
}
