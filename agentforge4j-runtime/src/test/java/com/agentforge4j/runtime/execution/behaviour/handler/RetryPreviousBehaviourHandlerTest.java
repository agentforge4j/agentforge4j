// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.runtime.InMemoryGeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetryPreviousBehaviourHandlerTest {

  private static final String RUN_ID = "run-1";
  private static final String WORKFLOW_ID = "wf-1";

  private EventRecorder eventRecorder;
  private ExecutableExecutor executableExecutor;
  private RetryPreviousBehaviourHandler handler;

  @BeforeEach
  void setUp() {
    eventRecorder = mock(EventRecorder.class);
    executableExecutor = mock(ExecutableExecutor.class);
    handler = new RetryPreviousBehaviourHandler(eventRecorder, new InMemoryGeneratedArtifactStore());
    handler.setExecutableExecutor(executableExecutor);
    when(executableExecutor.execute(any(Executable.class), any())).thenReturn(
        ExecutionOutcome.COMPLETED);
  }

  @Nested
  class AttemptCounter {

    @Test
    void first_attempt_sets_counter_to_one_and_records_event() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      assertThat(f.attemptCount()).isEqualTo(1);
      verify(eventRecorder).record(
          eq(RUN_ID),
          eq("s3"),
          eq(WorkflowEventType.STEP_RETRIED),
          eq("attempt 1 of 5, retryMode=SINGLE_STEP, retryStepId='s2'"),
          eq("runtime"));
    }

    @Test
    void subsequent_attempt_increments_existing_counter() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(2)
          .build();

      f.handle();

      assertThat(f.attemptCount()).isEqualTo(3);
    }

    @Test
    void max_attempts_reached_executes_fallback_without_incrementing_counter() {
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(5)
          .build();

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      assertThat(f.attemptCount()).isEqualTo(5);
      verify(executableExecutor, times(1)).execute(fallback, f.context());
      verify(executableExecutor, never()).execute(f.executable("s2"), f.context());
    }

    @Test
    void non_string_context_value_counter_treated_as_zero() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .contextValue("__retry_s2_attempts", new NumberContextValue(99, ContextProvenance.USER_SUPPLIED))
          .build();

      f.handle();

      assertThat(f.attemptCount()).isEqualTo(1);
    }

    @Test
    void malformed_string_counter_treated_as_zero() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCounter("abc")
          .build();

      f.handle();

      assertThat(f.attemptCount()).isEqualTo(1);
    }
  }

  @Nested
  class ClearEntriesFromUid {

    @Test
    void missing_retry_step_uid_throws_without_incrementing_counter() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("s3")
          .hasMessageContaining("s2")
          .hasMessageContaining("has not been executed yet");

      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
      verifyNoInteractions(executableExecutor);
    }

    @Test
    void clear_entries_from_uid_protects_attempt_counter_key() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(3)
          .stepExecuted("s3", 4, "out-s3")
          .contextWrittenAtUid("ctxX", 4, new StringContextValue("remove-me", ContextProvenance.USER_SUPPLIED))
          .attemptCounter("1")
          .build();

      int firstReallocatedUid = f.state().getStepExecutionUid().values().stream()
          .mapToInt(Integer::intValue)
          .max()
          .orElse(0) + 1;

      f.handle();

      assertThat(f.state().getContext()).containsKey(f.attemptKey());
      assertThat(f.state().getContext()).doesNotContainKey("ctxX");
      // s2 and s3's pre-retry uids/outputs are cleared by clearEntriesFromUid, then each is
      // re-allocated a fresh uid (continuing above the highest pre-retry uid) as it is
      // re-dispatched — no output is set for either since the mocked executableExecutor never
      // calls putStepOutput.
      assertThat(f.state().getStepExecutionUid())
          .containsEntry("s2", firstReallocatedUid)
          .containsEntry("s3", firstReallocatedUid + 1);
      assertThat(f.state().getStepOutputs()).doesNotContainKey("s2");
      assertThat(f.state().getStepOutputs()).doesNotContainKey("s3");
    }
  }

  @Nested
  class UidReallocation {

    @Test
    void single_step_retry_allocates_a_fresh_uid_distinct_from_the_original() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();
      when(f.context().allocateStepSequenceUid()).thenReturn(99);

      f.handle();

      assertThat(f.state().getStepExecutionUid()).containsEntry("s2", 99);
    }

    @Test
    void from_step_retry_allocates_a_fresh_uid_for_every_step_in_the_replayed_range() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .stepExecuted("s3", 3, "out-s3")
          .build();
      when(f.context().allocateStepSequenceUid()).thenReturn(10, 11);

      f.handle();

      assertThat(f.state().getStepExecutionUid()).containsEntry("s2", 10);
      assertThat(f.state().getStepExecutionUid()).containsEntry("s3", 11);
    }

    @Test
    void fallback_path_still_allocates_a_fresh_uid() {
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(5)
          .build();
      when(f.context().allocateStepSequenceUid()).thenReturn(7);

      f.handle();

      assertThat(f.state().getStepExecutionUid()).containsEntry("fallback", 7);
    }
  }

  @Nested
  class RetryModes {

    @Test
    void single_step_dispatches_retry_pivot_only() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();

      f.handle();

      verify(executableExecutor, times(1)).execute(f.executable("s2"), f.context());
    }

    @Test
    void from_step_all_completed_executes_sublist_before_owning_step() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .build();

      f.handle();

      InOrder order = inOrder(executableExecutor);
      order.verify(executableExecutor).execute(f.executable("s2"), f.context());
      order.verify(executableExecutor).execute(f.executable("s3"), f.context());
      verify(executableExecutor, never()).execute(f.executable("s4"), f.context());
    }

    @Test
    void from_step_stops_on_paused() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .outcomeForStep("s2", ExecutionOutcome.COMPLETED)
          .outcomeForStep("s3", ExecutionOutcome.PAUSED)
          .build();

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.PAUSED);
      verify(executableExecutor).execute(f.executable("s2"), f.context());
      verify(executableExecutor).execute(f.executable("s3"), f.context());
      verify(executableExecutor, never()).execute(f.executable("s4"), f.context());
    }

    @Test
    void from_step_stops_on_failed() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .outcomeForStep("s2", ExecutionOutcome.FAILED)
          .build();

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.FAILED);
      verify(executableExecutor, times(1)).execute(f.executable("s2"), f.context());
      verify(executableExecutor, never()).execute(f.executable("s3"), f.context());
    }

    @Test
    void from_step_retry_step_not_in_sequence_throws() {
      TestFixture f = fixture()
          .retryStepId("missing")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("not found in current sequence");
    }

    @Test
    void from_step_owning_step_not_in_sequence_throws() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("missing")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("owning step not found in current sequence");
    }

    @Test
    void from_step_retry_step_after_owning_step_throws() {
      TestFixture f = fixture()
          .retryStepId("s4")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s2")
          .sequence("s1", "s2", "s3", "s4")
          .retryStepExecuted(2)
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("must appear before owning step");
    }
  }

  @Nested
  class Wiring {

    @Test
    void unwired_handler_throws_illegal_argument_exception() {
      RetryPreviousBehaviourHandler unwired =
          new RetryPreviousBehaviourHandler(eventRecorder, new InMemoryGeneratedArtifactStore());
      TestFixture f = fixture()
          .handler(unwired)
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "executableExecutor must be configured on RetryPreviousBehaviourHandler");
    }
  }

  @Nested
  class EventRecording {

    @Test
    void step_retried_event_payload_on_successful_retry() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(1)
          .build();

      f.handle();

      ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
      verify(eventRecorder).record(
          eq(RUN_ID),
          eq("s3"),
          eq(WorkflowEventType.STEP_RETRIED),
          payload.capture(),
          eq("runtime"));
      assertThat(payload.getValue()).contains("attempt 2 of 5");
      assertThat(payload.getValue()).contains("retryMode=SINGLE_STEP");
      assertThat(payload.getValue()).contains("retryStepId='s2'");
    }

    @Test
    void step_retried_event_payload_on_fallback_path() {
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(5)
          .build();

      f.handle();

      ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
      verify(eventRecorder).record(
          eq(RUN_ID),
          eq("s3"),
          eq(WorkflowEventType.STEP_RETRIED),
          payload.capture(),
          eq("runtime"));
      assertThat(payload.getValue()).contains("maxAttempts 5 reached");
      assertThat(payload.getValue()).contains("retryStepId 's2'");
    }
  }

  private FixtureBuilder fixture() {
    return new FixtureBuilder(executableExecutor, handler);
  }

  private static StepDefinition fallbackStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", stepId + ".out", StepTransition.AUTO))
        .withContextMapping(ContextMapping.none())
        .build();
  }

  private static final class FixtureBuilder {

    private final ExecutableExecutor defaultExecutableExecutor;
    private final RetryPreviousBehaviourHandler defaultHandler;
    private RetryPreviousBehaviourHandler handler;
    private String retryStepId = "s2";
    private RetryMode retryMode = RetryMode.SINGLE_STEP;
    private int maxAttempts = 5;
    private Executable fallback = fallbackStep("fallback");
    private String owningStepId = "s3";
    private List<String> sequence = List.of("s1", "s2", "s3");
    private Integer retryUid;
    private Integer attemptCount;
    private String attemptCounterString;
    private final Map<String, ContextValue> extraContext = new LinkedHashMap<>();
    private final Map<String, StepSeed> stepSeeds = new LinkedHashMap<>();
    private final Map<String, ExecutionOutcome> outcomesByStep = new LinkedHashMap<>();

    FixtureBuilder(
        ExecutableExecutor defaultExecutableExecutor,
        RetryPreviousBehaviourHandler defaultHandler) {
      this.defaultExecutableExecutor = defaultExecutableExecutor;
      this.defaultHandler = defaultHandler;
    }

    FixtureBuilder handler(RetryPreviousBehaviourHandler handler) {
      this.handler = handler;
      return this;
    }

    FixtureBuilder retryStepId(String retryStepId) {
      this.retryStepId = retryStepId;
      return this;
    }

    FixtureBuilder retryMode(RetryMode retryMode) {
      this.retryMode = retryMode;
      return this;
    }

    FixtureBuilder maxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    FixtureBuilder fallback(Executable fallback) {
      this.fallback = fallback;
      return this;
    }

    FixtureBuilder owningStepId(String owningStepId) {
      this.owningStepId = owningStepId;
      return this;
    }

    FixtureBuilder sequence(String... stepIds) {
      this.sequence = List.of(stepIds);
      return this;
    }

    FixtureBuilder retryStepExecuted(int uid) {
      this.retryUid = uid;
      return this;
    }

    FixtureBuilder attemptCount(int count) {
      this.attemptCount = count;
      return this;
    }

    FixtureBuilder attemptCounter(String value) {
      this.attemptCounterString = value;
      return this;
    }

    FixtureBuilder contextValue(String key, ContextValue value) {
      extraContext.put(key, value);
      return this;
    }

    FixtureBuilder stepExecuted(String stepId, int uid, String output) {
      stepSeeds.put(stepId, new StepSeed(uid, output));
      return this;
    }

    FixtureBuilder contextWrittenAtUid(String key, int uid, ContextValue value) {
      extraContext.put(key, value);
      stepSeeds.put("__ctxUid__" + key, new StepSeed(uid, null));
      return this;
    }

    FixtureBuilder outcomeForStep(String stepId, ExecutionOutcome outcome) {
      outcomesByStep.put(stepId, outcome);
      return this;
    }

    TestFixture build() {
      RetryPreviousBehaviour behaviour = new RetryPreviousBehaviour(
          retryStepId, retryMode, maxAttempts, fallback);
      StepDefinition owningStep = StepDefinition.builder()
          .withStepId(owningStepId)
          .withName(owningStepId)
          .withBehaviour(behaviour)
          .withContextMapping(ContextMapping.none())
          .build();
      WorkflowState state = new WorkflowState(
          RUN_ID, WORKFLOW_ID, null, Instant.parse("2026-05-01T12:00:00Z"));

      String attemptKey = "__retry_" + retryStepId + "_attempts";
      if (attemptCount != null) {
        state.putContextValue(attemptKey, new StringContextValue(String.valueOf(attemptCount), ContextProvenance.USER_SUPPLIED));
      } else if (attemptCounterString != null) {
        state.putContextValue(attemptKey, new StringContextValue(attemptCounterString, ContextProvenance.USER_SUPPLIED));
      }
      extraContext.forEach(state::putContextValue);

      if (retryUid != null) {
        state.putStepExecutionUid(retryStepId, retryUid);
        state.putStepOutput(retryStepId, "retry-out");
      }
      for (Map.Entry<String, StepSeed> entry : stepSeeds.entrySet()) {
        String stepId = entry.getKey();
        if (stepId.startsWith("__ctxUid__")) {
          String contextKey = stepId.substring("__ctxUid__".length());
          state.putContextKeyWrittenAtUid(contextKey, entry.getValue().uid());
          continue;
        }
        StepSeed seed = entry.getValue();
        state.putStepExecutionUid(stepId, seed.uid());
        if (seed.output() != null) {
          state.putStepOutput(stepId, seed.output());
        }
      }

      Map<String, Executable> executables = new LinkedHashMap<>();
      for (String stepId : sequence) {
        executables.put(stepId, mockStepExecutable(stepId));
      }

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.getState()).thenReturn(state);
      when(context.getCurrentSequenceStepIds()).thenReturn(sequence);
      when(context.getCurrentSequenceExecutables()).thenReturn(executables);
      // Mirrors the real ExecutionContext's monotonic-uid-seeded-from-persisted-state behaviour, so
      // a retry target dispatched via executeStep (which allocates a fresh uid) gets a deterministic,
      // always-higher-than-anything-seeded value instead of Mockito's default 0 for every call.
      AtomicInteger uidCounter = new AtomicInteger(highestSeededUid(state) + 1);
      when(context.allocateStepSequenceUid()).thenAnswer(invocation -> uidCounter.getAndIncrement());

      RetryPreviousBehaviourHandler resolvedHandler =
          handler != null ? handler : defaultHandler;

      return new TestFixture(
          resolvedHandler,
          defaultExecutableExecutor,
          owningStep,
          behaviour,
          state,
          context,
          executables,
          attemptKey,
          outcomesByStep);
    }

    private static int highestSeededUid(WorkflowState state) {
      return state.getStepExecutionUid().values().stream()
          .mapToInt(Integer::intValue)
          .max()
          .orElse(0);
    }

    private static Executable mockStepExecutable(String stepId) {
      return StepDefinition.builder()
          .withStepId(stepId)
          .withName(stepId)
          .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", stepId + ".out",
              StepTransition.AUTO))
          .withContextMapping(ContextMapping.none())
          .build();
    }
  }

  private record StepSeed(int uid, String output) {
  }

  private record TestFixture(
      RetryPreviousBehaviourHandler handler,
      ExecutableExecutor executableExecutor,
      StepDefinition owningStep,
      RetryPreviousBehaviour behaviour,
      WorkflowState state,
      ExecutionContext context,
      Map<String, Executable> executables,
      String attemptKey,
      Map<String, ExecutionOutcome> outcomesByStep) {

    ExecutionOutcome handle() {
      outcomesByStep.forEach((stepId, outcome) ->
          when(executableExecutor.execute(executable(stepId), context)).thenReturn(outcome));
      return handler.handle(owningStep, behaviour, context);
    }

    Executable executable(String stepId) {
      return executables.get(stepId);
    }

    int attemptCount() {
      return state.getContextValue(attemptKey)
          .filter(StringContextValue.class::isInstance)
          .map(v -> Integer.parseInt(((StringContextValue) v).value()))
          .orElse(0);
    }
  }
}
