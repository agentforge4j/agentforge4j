// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.waste;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.api.ModelTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link WasteDetectorHistoryStore}'s round-trip behavior and, specifically, that its
 * persisted history survives {@link WorkflowState#clearEntriesFromUid(int)} — the reserved
 * {@code __wasteDetectorHistory.*} namespace is exactly why persisted (context-map-backed)
 * storage was chosen over an in-memory field: an in-memory field would not survive a state
 * reload from a repository, and a plain (non-reserved) context key would not survive a
 * {@code RETRY_PREVIOUS} rewind.
 */
class WasteDetectorHistoryStoreTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static WorkflowState state() {
    return new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void invocationHistoryRoundTrips() {
    WorkflowState state = state();
    WasteDetectorInvocationHistory history = new WasteDetectorInvocationHistory("step-1", "agent-1",
        "ctx-fp", "input-fp", ModelTier.STANDARD);

    WasteDetectorHistoryStore.writeInvocation(state, history, mapper);
    Optional<WasteDetectorInvocationHistory> read = WasteDetectorHistoryStore.readInvocation(state,
        "step-1", mapper);

    assertThat(read).contains(history);
  }

  @Test
  void loopHistoryRoundTrips() {
    WorkflowState state = state();
    WasteDetectorLoopHistory history = new WasteDetectorLoopHistory("bp-1", "ctx-fp",
        Set.of("out-fp-1", "out-fp-2"));

    WasteDetectorHistoryStore.writeLoop(state, history, mapper);
    Optional<WasteDetectorLoopHistory> read = WasteDetectorHistoryStore.readLoop(state, "bp-1",
        mapper);

    assertThat(read).contains(history);
  }

  @Test
  void invocationHistorySurvivesClearEntriesFromUid() {
    WorkflowState state = state();
    WasteDetectorHistoryStore.writeInvocation(state,
        new WasteDetectorInvocationHistory("step-1", "agent-1", "ctx-fp", "input-fp",
            ModelTier.STANDARD),
        mapper);

    // Simulates a RETRY_PREVIOUS rewind clearing everything from step-execution uid 0 onward —
    // the most aggressive possible rewind.
    state.clearEntriesFromUid(0);

    assertThat(WasteDetectorHistoryStore.readInvocation(state, "step-1", mapper)).isPresent();
  }

  @Test
  void loopHistorySurvivesClearEntriesFromUid() {
    WorkflowState state = state();
    WasteDetectorHistoryStore.writeLoop(state,
        new WasteDetectorLoopHistory("bp-1", "ctx-fp", Set.of("out-fp-1")), mapper);

    state.clearEntriesFromUid(0);

    assertThat(WasteDetectorHistoryStore.readLoop(state, "bp-1", mapper)).isPresent();
  }

  @Test
  void readReturnsEmptyWhenNoHistoryIsStoredYet() {
    WorkflowState state = state();

    assertThat(WasteDetectorHistoryStore.readInvocation(state, "step-1", mapper)).isEmpty();
    assertThat(WasteDetectorHistoryStore.readLoop(state, "bp-1", mapper)).isEmpty();
  }

  @Test
  void writeInvocationOverwritesThePriorEntryForTheSameStep() {
    WorkflowState state = state();
    WasteDetectorHistoryStore.writeInvocation(state,
        new WasteDetectorInvocationHistory("step-1", "agent-1", "ctx-fp-1", "input-fp-1", null),
        mapper);
    WasteDetectorHistoryStore.writeInvocation(state,
        new WasteDetectorInvocationHistory("step-1", "agent-1", "ctx-fp-2", "input-fp-2", null),
        mapper);

    Optional<WasteDetectorInvocationHistory> read = WasteDetectorHistoryStore.readInvocation(state,
        "step-1", mapper);

    assertThat(read).isPresent();
    assertThat(read.get().scopedContextFingerprint()).isEqualTo("ctx-fp-2");
  }
}
