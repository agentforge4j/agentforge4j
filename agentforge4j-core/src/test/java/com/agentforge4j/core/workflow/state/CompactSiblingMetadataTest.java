// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactSiblingMetadataTest {

  private static CompactSiblingMetadata valid() {
    return new CompactSiblingMetadata("requirements", "abc123", new DeterministicExtract(), 800, 200,
        "compact-step", new CompactionPolicy(100, 1));
  }

  @Test
  void retainsProvenance() {
    CompactSiblingMetadata metadata = valid();

    assertThat(metadata.sourceId()).isEqualTo("requirements");
    assertThat(metadata.estimatedUnitsBefore()).isEqualTo(800);
    assertThat(metadata.estimatedUnitsAfter()).isEqualTo(200);
    assertThat(metadata.mode()).isInstanceOf(DeterministicExtract.class);
  }

  @Test
  void rejectsBlankSourceId() {
    assertThatThrownBy(() -> new CompactSiblingMetadata(" ", "fp", new DeterministicExtract(), 1, 1,
        "s", new CompactionPolicy(0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeUnits() {
    assertThatThrownBy(() -> new CompactSiblingMetadata("id", "fp", new DeterministicExtract(), -1, 0,
        "s", new CompactionPolicy(0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullModeAndPolicy() {
    assertThatThrownBy(() -> new CompactSiblingMetadata("id", "fp", null, 1, 1, "s",
        new CompactionPolicy(0, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
        () -> new CompactSiblingMetadata("id", "fp", new DeterministicExtract(), 1, 1, "s", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
