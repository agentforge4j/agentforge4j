// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.file.ArtifactDescriptor;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.InMemoryGeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForEachLoopStrategyTest {

  private static final String LIST_KEY = "items";
  private static final String BLUEPRINT_ID = "bp-for-each";

  @Test
  void fingerprint_is_provenance_independent() {
    ContextValueList userSupplied = new ContextValueList(
        List.of(new StringContextValue("a", ContextProvenance.USER_SUPPLIED),
            new StringContextValue("b", ContextProvenance.USER_SUPPLIED)),
        ContextProvenance.USER_SUPPLIED);
    ContextValueList systemGenerated = new ContextValueList(
        List.of(new StringContextValue("a", ContextProvenance.SYSTEM_GENERATED),
            new StringContextValue("b", ContextProvenance.LLM_GENERATED)),
        ContextProvenance.SYSTEM_GENERATED);

    // Identical values, different container and element provenance -> identical fingerprint:
    // the content hash must track values, not provenance metadata.
    assertThat(ForEachLoopStrategy.fingerprint(userSupplied))
        .isEqualTo(ForEachLoopStrategy.fingerprint(systemGenerated));

    ContextValueList differentContent = new ContextValueList(
        List.of(new StringContextValue("a", ContextProvenance.USER_SUPPLIED),
            new StringContextValue("c", ContextProvenance.USER_SUPPLIED)),
        ContextProvenance.USER_SUPPLIED);
    assertThat(ForEachLoopStrategy.fingerprint(userSupplied))
        .isNotEqualTo(ForEachLoopStrategy.fingerprint(differentContent));
  }

  private EventRecorder eventRecorder;
  private MaxIterationsHandler maxIterationsHandler;
  private StepSequenceExecutor stepSequenceExecutor;
  private InMemoryGeneratedArtifactStore generatedArtifactStore;
  private ForEachLoopStrategy strategy;
  private WorkflowState state;
  private ExecutionContext executionContext;
  private BlueprintDefinition blueprint;

  @BeforeEach
  void setUp() {
    eventRecorder = mock(EventRecorder.class);
    maxIterationsHandler = new MaxIterationsHandler(eventRecorder,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    stepSequenceExecutor = mock(StepSequenceExecutor.class);
    generatedArtifactStore = new InMemoryGeneratedArtifactStore();
    strategy = new ForEachLoopStrategy(stepSequenceExecutor, eventRecorder, maxIterationsHandler,
        new ObjectMapper(), WasteSignalPolicy.NO_OP, generatedArtifactStore);
    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T12:00:00Z"));
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1",
        "wf-1",
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(dummyStep()),
        List.of(),
        List.of());
    executionContext = new ExecutionContext(state, workflow, 32);
    blueprint = new BlueprintDefinition(
        BLUEPRINT_ID,
        "for-each blueprint",
        new BlueprintBehaviour(forEachConfig(false), StepTransition.AUTO),
        List.of(dummyStep()));
  }

  @Test
  void unchanged_list_resumes_at_stored_iteration() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> calls.getAndIncrement() == 0
            ? ExecutionOutcome.PAUSED
            : ExecutionOutcome.COMPLETED);

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isEqualTo(1);
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isPresent();

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // iteration 1 (paused), then resume re-runs iteration 1 and runs iteration 2
    assertThat(calls.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void mutated_list_fails_when_mutation_not_allowed() {
    putList("a", "b");
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.PAUSED);

    strategy.iterate(blueprint, forEachConfig(false), executionContext);
    putList("x", "y");

    assertThatThrownBy(() -> strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(LIST_KEY)
        .hasMessageContaining(BLUEPRINT_ID)
        .hasMessageContaining("run-1")
        .hasMessageContaining("allowForEachListMutation=true");
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void mutated_list_restarts_when_mutation_allowed() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> calls.getAndIncrement() == 0
            ? ExecutionOutcome.PAUSED
            : ExecutionOutcome.COMPLETED);

    strategy.iterate(blueprint, forEachConfig(true), executionContext);
    putList("x", "y");

    assertThat(strategy.iterate(blueprint, forEachConfig(true), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // iteration 1 (paused), then mutation restart runs iterations 1 and 2 on the new list
    assertThat(calls.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void mutated_list_restart_rewinds_stale_body_outputs_so_steps_rerun() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          if (calls.getAndIncrement() == 0) {
            // Simulate the real executor recording the body step's output before pausing — the
            // entry StepSequenceExecutor.shouldSkip keys on during a later drive.
            int uid = executionContext.allocateStepSequenceUid();
            state.putStepExecutionUid("dummy", uid);
            state.putStepOutput("dummy", "stale-output-from-abandoned-iteration");
            return ExecutionOutcome.PAUSED;
          }
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(true), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    putList("x", "y");

    assertThat(strategy.iterate(blueprint, forEachConfig(true), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // The restart must rewind the abandoned iteration's body range: a surviving output would make
    // StepSequenceExecutor.shouldSkip silently skip the body on every restarted iteration.
    assertThat(state.getStepOutput("dummy")).isEmpty();
    assertThat(state.getStepExecutionUid("dummy")).isEmpty();
    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void mutated_list_restart_evicts_abandoned_iterations_generated_artifact_bytes() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          if (calls.getAndIncrement() == 0) {
            // Simulate the real executor: the abandoned iteration's body emitted an artifact via
            // CREATE_FILE before pausing — descriptor on state, bytes in the run-scoped store.
            int uid = executionContext.allocateStepSequenceUid();
            state.putStepExecutionUid("dummy", uid);
            state.addGeneratedArtifactDescriptor(
                new ArtifactDescriptor("abandoned.txt", "hash", "dummy", uid));
            generatedArtifactStore.register("run-1", "dummy", "abandoned.txt", "stale bytes");
            return ExecutionOutcome.PAUSED;
          }
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(true), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(generatedArtifactStore.find("run-1", "abandoned.txt")).isPresent();
    putList("x", "y");

    assertThat(strategy.iterate(blueprint, forEachConfig(true), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // F-1 regression: the restart must evict the abandoned iteration's captured artifact bytes,
    // not just its descriptor from WorkflowState, mirroring DefaultWorkflowRuntime.retry and
    // RetryPreviousBehaviourHandler's paired GeneratedArtifactEviction.evictFromUid call.
    assertThat(generatedArtifactStore.find("run-1", "abandoned.txt")).isEmpty();
  }

  @Test
  void new_iteration_reruns_body_but_preserves_previous_iterations_context_writes() {
    putList("a", "b");
    List<Boolean> outputPresentAtIterationEntry = new ArrayList<>();
    List<Boolean> draftPresentAtIterationEntry = new ArrayList<>();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          // Observed at body entry, i.e. after markLoopIterationStart's iteration-boundary clear.
          outputPresentAtIterationEntry.add(state.getStepOutput("dummy").isPresent());
          draftPresentAtIterationEntry.add(state.getContextValue("draft").isPresent());
          // Simulate the real executor and body: record the step's uid/output, and write a
          // non-reserved context value the way SetContextCommandHandler does (value + written-at uid).
          int uid = executionContext.allocateStepSequenceUid();
          state.putStepExecutionUid("dummy", uid);
          state.putStepOutput("dummy", "out");
          state.putContextValue("draft",
              new StringContextValue("draft", ContextProvenance.LLM_GENERATED));
          state.putContextKeyWrittenAtUid("draft", uid);
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);

    // Iteration 2 must re-run the body (previous output cleared at the boundary) while still
    // seeing iteration 1's context write — advancing a loop is not a rewind, and cross-iteration
    // context handoff is what a rework/refinement loop depends on.
    assertThat(outputPresentAtIterationEntry).containsExactly(false, false);
    assertThat(draftPresentAtIterationEntry).containsExactly(false, true);
    assertThat(state.getContextValue("draft")).isPresent();
  }

  @Test
  void retry_across_a_paused_iteration_with_a_changed_list_restarts_instead_of_throwing() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          if (calls.getAndIncrement() == 0) {
            int uid = executionContext.allocateStepSequenceUid();
            state.putStepExecutionUid("dummy", uid);
            return ExecutionOutcome.PAUSED;
          }
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isEqualTo(1);
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isPresent();

    // Simulate a retry/rewind crossing this loop's in-progress iteration: WorkflowState's rewind
    // chokepoint (WorkflowState.clearEntriesFromUid) clears the cursor, body-start-uid, and list
    // fingerprint together.
    int bodyStartUid = state.getLoopIterationBodyStartUid(BLUEPRINT_ID);
    state.clearEntriesFromUid(bodyStartUid, Set.of());
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();

    // The redrive's upstream step legitimately produces a different list on this retry — the
    // ordinary reason to retry it — which must not be misread as a disallowed pause/resume
    // mutation now that the cursor and fingerprint are consistently cleared together.
    putList("x", "y");

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    // A fresh entry re-runs both iterations of the new list rather than throwing
    // IllegalStateException for a "list changed between pause and resume".
    assertThat(calls.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void disallowed_mutation_throw_path_also_evicts_the_abandoned_iterations_generated_artifact_bytes() {
    putList("a", "b");
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          if (calls.getAndIncrement() == 0) {
            int uid = executionContext.allocateStepSequenceUid();
            state.putStepExecutionUid("dummy", uid);
            state.addGeneratedArtifactDescriptor(
                new ArtifactDescriptor("abandoned.txt", "hash", "dummy", uid));
            generatedArtifactStore.register("run-1", "dummy", "abandoned.txt", "stale bytes");
            return ExecutionOutcome.PAUSED;
          }
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(generatedArtifactStore.find("run-1", "abandoned.txt")).isPresent();
    putList("x", "y");

    // With allowForEachListMutation=false the strategy throws instead of restarting, but the
    // abandoned iteration's captured artifact bytes must still be evicted before it does — the
    // same guarantee the allowed-mutation restart path gives, so this throw path does not leak
    // artifact bytes against the run's artifact-count bound if a future change relaxes the
    // terminal-status cleanup this currently relies on.
    assertThatThrownBy(
        () -> strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isInstanceOf(IllegalStateException.class);
    assertThat(generatedArtifactStore.find("run-1", "abandoned.txt")).isEmpty();
  }

  @Test
  void body_exception_rewinds_the_aborted_iterations_stale_body_outputs_before_rethrowing() {
    putList("a", "b");
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          // Simulate the real executor recording the body step's output before the body throws —
          // the entry StepSequenceExecutor.shouldSkip keys on during a later retry-driven redrive.
          int uid = executionContext.allocateStepSequenceUid();
          state.putStepExecutionUid("dummy", uid);
          state.putStepOutput("dummy", "stale-output-from-aborted-iteration");
          throw new IllegalStateException("boom");
        });

    assertThatThrownBy(() -> strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    // Pre-fix, the catch block only cleared the cursor/fingerprint (clearLoopState), leaving the
    // aborted iteration's step output/uid in place — a later retry of a downstream step could then
    // silently skip-guard this step instead of re-running it. The fix rewinds the full body range
    // via restartLoop, the same as the sibling mutation/ceiling-exceeded restart paths.
    assertThat(state.getStepOutput("dummy")).isEmpty();
    assertThat(state.getStepExecutionUid("dummy")).isEmpty();
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  @Test
  void body_exception_also_evicts_the_aborted_iterations_generated_artifact_bytes() {
    putList("a", "b");
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          int uid = executionContext.allocateStepSequenceUid();
          state.putStepExecutionUid("dummy", uid);
          state.addGeneratedArtifactDescriptor(
              new ArtifactDescriptor("aborted.txt", "hash", "dummy", uid));
          generatedArtifactStore.register("run-1", "dummy", "aborted.txt", "stale bytes");
          throw new IllegalStateException("boom");
        });

    assertThatThrownBy(() -> strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isInstanceOf(IllegalStateException.class);

    // Mirrors the mutation/ceiling-exceeded restart paths' artifact-eviction guarantee: the thrown
    // iteration's captured artifact bytes must not leak against the run's artifact-count bound.
    assertThat(generatedArtifactStore.find("run-1", "aborted.txt")).isEmpty();
  }

  @Test
  void cancelled_clears_cursor_and_fingerprint() {
    putList("a", "b");
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          state.setStatus(WorkflowStatus.CANCELLED);
          return ExecutionOutcome.COMPLETED;
        });

    assertThat(strategy.iterate(blueprint, forEachConfig(false), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    assertThat(state.getForEachListFingerprint(BLUEPRINT_ID)).isEmpty();
  }

  private void putList(String... values) {
    List<ContextValue> items = new ArrayList<>();
    for (String value : values) {
      items.add(new StringContextValue(value, ContextProvenance.USER_SUPPLIED));
    }
    state.putContextValue(LIST_KEY, new ContextValueList(items, ContextProvenance.USER_SUPPLIED));
  }

  private static StepDefinition dummyStep() {
    return StepDefinition.builder()
        .withStepId("dummy")
        .withName("dummy")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "dummy.out", StepTransition.AUTO))
        .build();
  }

  private static LoopConfig forEachConfig(boolean allowMutation) {
    return new LoopConfig(
        LoopTerminationStrategy.FOR_EACH,
        LIST_KEY,
        null,
        10,
        null,
        allowMutation,
        null);
  }
}
