// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.CompactSiblingMetadata;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.behaviour.CompactionPolicy;
import com.agentforge4j.core.workflow.step.behaviour.DeterministicExtract;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link CompactSiblingStore}'s fail-loud/defensive branches: neither is exercised by the
 * happy-path coverage in {@code CompactBehaviourHandlerTest}/{@code RequestContextCommandHandlerTest}.
 */
class CompactSiblingStoreTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static WorkflowState state() {
    return new WorkflowState("run-1", "wf", null, Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void readRejectsAWrongContextValueSubtypeAtTheCompactKey() {
    WorkflowState state = state();
    String sourceId = "LEDGER_SECTION:requirements";
    state.putContextValue(ReservedContextKeys.compactKey(sourceId),
        new StringContextValue("not json content", ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> CompactSiblingStore.read(state, sourceId, mapper))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(sourceId)
        .hasMessageContaining("does not hold JSON content");
  }

  @Test
  void readFailsLoudOnMalformedStoredJson() {
    WorkflowState state = state();
    String sourceId = "LEDGER_SECTION:requirements";
    // Write directly at the raw context layer to simulate corrupted/foreign-written state: the
    // metadata field is missing, which is exactly what CompactSiblingStore.write never produces
    // itself, so this can only be exercised by writing the reserved key out of band.
    state.putContextValue(ReservedContextKeys.compactKey(sourceId),
        new JsonContextValue("{\"content\":\"x\"}", ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> CompactSiblingStore.read(state, sourceId, mapper))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(sourceId)
        .hasMessageContaining("Failed to parse stored compact sibling");
  }

  @Test
  void isFreshIsFalseWhenNoSiblingIsPresent() {
    assertThat(CompactSiblingStore.isFresh(Optional.empty(), "fp-1")).isFalse();
  }

  @Test
  void isFreshComparesTheStoredFingerprint() {
    CompactSiblingMetadata matching = new CompactSiblingMetadata("LEDGER_SECTION:requirements",
        "fp-1", new DeterministicExtract(), 100, 10, "compact-step", new CompactionPolicy(0, 0));
    CompactSibling sibling = new CompactSibling("compact", matching);

    assertThat(CompactSiblingStore.isFresh(Optional.of(sibling), "fp-1")).isTrue();
    assertThat(CompactSiblingStore.isFresh(Optional.of(sibling), "fp-2")).isFalse();
  }
}
