// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRiskMetadataTest {

  @Test
  void conservativeIsTheHighestSafeRisk() {
    assertThat(ToolRiskMetadata.conservative().mutating()).isTrue();
  }

  @Test
  void carriesTheSuppliedMutationSignal() {
    assertThat(new ToolRiskMetadata(false).mutating()).isFalse();
    assertThat(new ToolRiskMetadata(true).mutating()).isTrue();
  }

  @Test
  void descriptorRejectsNullRiskMetadata() {
    ToolSource toolSource = new ToolSource("provider:test", "do_thing", ToolSourceKind.IN_PROCESS);
    assertThatThrownBy(() -> new ToolDescriptor(
        "domain.do_thing", null, null, null, null,
        toolSource, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("riskMetadata");
  }
}
