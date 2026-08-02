// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.collection.CollectionPhase;
import com.agentforge4j.core.workflow.collection.CollectionState;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowStateCollectionTest {

  private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");

  private static CollectionState open(String stepId, long version) {
    return new CollectionState(stepId, CollectionPhase.OPEN, NOW, null, null, null, null, null, null,
        version);
  }

  private static WorkflowState newState() {
    return new WorkflowState("run-1", "wf-1", null, NOW);
  }

  @Test
  void putAndGetCollectionState() {
    WorkflowState state = newState();
    assertThat(state.getCollectionState("step-1")).isEmpty();

    state.putCollectionState(open("step-1", 1L));

    assertThat(state.getCollectionState("step-1")).get()
        .extracting(CollectionState::version).isEqualTo(1L);
    assertThat(state.getCollectionStateByStepId()).containsOnlyKeys("step-1");
  }

  @Test
  void putReplacesPreviousSnapshotForSameStep() {
    WorkflowState state = newState();
    state.putCollectionState(open("step-1", 1L));
    state.putCollectionState(open("step-1", 2L));

    assertThat(state.getCollectionState("step-1")).get()
        .extracting(CollectionState::version).isEqualTo(2L);
  }

  @Test
  void collectionStateMapViewIsUnmodifiable() {
    WorkflowState state = newState();
    state.putCollectionState(open("step-1", 1L));
    assertThatThrownBy(() -> state.getCollectionStateByStepId().put("x", open("x", 1L)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void replaceCollectionStatesIsNullTolerantAndFiltersBlankKeysAndNullValues() {
    WorkflowState state = newState();
    state.putCollectionState(open("step-1", 1L));

    state.replaceCollectionStates(null);
    assertThat(state.getCollectionStateByStepId()).isEmpty();

    Map<String, CollectionState> incoming = new HashMap<>();
    incoming.put("step-2", open("step-2", 5L));
    incoming.put(" ", open("blank", 1L));
    incoming.put("step-3", null);
    state.replaceCollectionStates(incoming);

    assertThat(state.getCollectionStateByStepId()).containsOnlyKeys("step-2");
  }

  @Test
  void replaceCollectionStatesRejectsKeyValueStepIdMismatch() {
    WorkflowState state = newState();
    Map<String, CollectionState> incoming = new HashMap<>();
    incoming.put("step-X", open("step-Y", 1L));

    assertThatThrownBy(() -> state.replaceCollectionStates(incoming))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clearEntriesFromUidDoesNotClearCollectionState() {
    WorkflowState state = newState();
    state.putCollectionState(open("step-1", 1L));

    state.clearEntriesFromUid(0, Set.of());

    assertThat(state.getCollectionState("step-1")).get()
        .extracting(CollectionState::version).isEqualTo(1L);
  }

  @Test
  void snapshotDeepCopiesCollectionStateMap() {
    WorkflowState original = newState();
    original.putCollectionState(open("step-1", 1L));

    WorkflowState copy = original.snapshot();
    assertThat(copy.getCollectionState("step-1")).get()
        .extracting(CollectionState::version).isEqualTo(1L);

    // Mutating the copy must not affect the original (separate map instances).
    copy.putCollectionState(open("step-2", 9L));
    assertThat(original.getCollectionStateByStepId()).containsOnlyKeys("step-1");
    assertThat(copy.getCollectionStateByStepId()).containsOnlyKeys("step-1", "step-2");
  }
}
