// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.runtime.InMemoryGeneratedArtifactStore;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.RetryPolicyAttemptCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.ArrayList;
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
          .contextValue("__retry_previous_attempts:s2", new NumberContextValue(99, ContextProvenance.USER_SUPPLIED))
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
  class CompositeRangeRejection {

    @Test
    void from_step_rejects_when_a_blueprint_ref_sits_inside_the_replay_range() {
      // Mirrors [A, blueprint(writes K to context), V, R(FROM_STEP targeting A)]: A="s1",
      // blueprint="bp1" (invisible to the plain-id sequence, present only in the full executable
      // list), V="s3" (already completed, output "v-out"), R (owning)="s4". K="k" simulates the
      // blueprint's context write.
      TestFixture f = fixture()
          .retryStepId("s1")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s3", "s4")
          .retryStepExecuted(1)
          .stepExecuted("s3", 3, "v-out")
          .contextValue("k", new StringContextValue("blueprint-value", ContextProvenance.SYSTEM_GENERATED))
          .build();
      BlueprintRef composite = new BlueprintRef("bp1");
      List<Executable> fullSequenceWithComposite = new ArrayList<>(List.of(
          f.executable("s1"), composite, f.executable("s3"), f.executable("s4")));
      when(f.context().getCurrentSequenceExecutableList()).thenReturn(fullSequenceWithComposite);

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("s4")
          .hasMessageContaining("s1")
          .hasMessageContaining("BlueprintRef:bp1");

      // Rejected before any mutation: no dispatch, no attempt-counter write, K (blueprint's context
      // write) and A's/V's outputs completely unchanged.
      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
      assertThat(((StringContextValue) f.state().getContext().get("k")).value())
          .isEqualTo("blueprint-value");
      assertThat(f.state().getStepOutputs()).containsEntry("s1", "retry-out");
      assertThat(f.state().getStepOutputs()).containsEntry("s3", "v-out");
    }

    @Test
    void from_step_rejects_a_trailing_composite_with_nothing_plain_after_it_in_range() {
      // Mirrors retry-loop.workflow's shape: anchor(INPUT)="s1" -> loop-bp(composite, trailing,
      // nothing plain follows it before the owning step) -> retry(FROM_STEP targeting "s1")="s2".
      // executeFromStep's own inline replay loop walks getCurrentSequenceStepIds() (plain steps
      // only), which never contains the composite at all, so it only ever re-executes "s1" here.
      // Even though nothing re-reads the composite's cleared state within THIS dispatch, whatever
      // the top-level sequence runs immediately after "s2" in the same drive (if it does not itself
      // pause) can observe the composite's now-missing state — there is no safe "trailing composite"
      // shape, so this is rejected unconditionally, before any mutation.
      TestFixture f = fixture()
          .retryStepId("s1")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s2")
          .sequence("s1", "s2")
          .retryStepExecuted(1)
          .build();
      BlueprintRef trailingComposite = new BlueprintRef("loop-bp");
      List<Executable> fullSequenceWithTrailingComposite = new ArrayList<>(List.of(
          f.executable("s1"), trailingComposite, f.executable("s2")));
      when(f.context().getCurrentSequenceExecutableList())
          .thenReturn(fullSequenceWithTrailingComposite);

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("BlueprintRef:loop-bp");

      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }

    @Test
    void from_step_rejects_a_nested_workflow_definition_sitting_inside_the_replay_range() {
      // Same shape and rationale as the BlueprintRef case above, but with a nested WorkflowDefinition
      // composite — the other kind of non-step executable the full sequence list can carry.
      TestFixture f = fixture()
          .retryStepId("s1")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s3")
          .sequence("s1", "s3")
          .retryStepExecuted(1)
          .build();
      StepDefinition nestedWorkflowStep = StepDefinition.builder()
          .withStepId("nested-step")
          .withName("nested-step")
          .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "nested.out",
              StepTransition.AUTO))
          .withContextMapping(ContextMapping.none())
          .build();
      WorkflowDefinition nestedWorkflowComposite = new WorkflowDefinition(
          "nested-wf", "nested-wf", null, null, null, null, null,
          WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
          List.of(nestedWorkflowStep), List.of());
      List<Executable> fullSequenceWithNestedWorkflow = new ArrayList<>(List.of(
          f.executable("s1"), nestedWorkflowComposite, f.executable("s3")));
      when(f.context().getCurrentSequenceExecutableList())
          .thenReturn(fullSequenceWithNestedWorkflow);

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("WorkflowDefinition:nested-wf");

      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }

    @Test
    void single_step_still_rejects_a_composite_with_nothing_plain_after_it_in_range() {
      // Same physical shape as the FROM_STEP trailing-composite case above (composite directly
      // before the owning step, nothing plain after it), but in SINGLE_STEP mode — where the
      // divergent reasoning applies: SINGLE_STEP's own dispatch never inline-replays anything in the
      // between-span, composite or plain, and there is no leading plain-step-in-range pause
      // guaranteeing a future top-level resume-from-0 (the retry pivot sits outside the span
      // entirely). So, unlike FROM_STEP, a trailing composite here is NOT safe and must still be
      // rejected.
      TestFixture f = fixture()
          .retryStepId("s1")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s3")
          .retryStepExecuted(1)
          .build();
      BlueprintRef trailingComposite = new BlueprintRef("bp1");
      List<Executable> fullSequenceWithTrailingComposite = new ArrayList<>(List.of(
          f.executable("s1"), trailingComposite, f.executable("s3")));
      when(f.context().getCurrentSequenceExecutableList())
          .thenReturn(fullSequenceWithTrailingComposite);

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("BlueprintRef:bp1");

      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }

    @Test
    void single_step_rejects_when_a_composite_sits_between_a_non_adjacent_target_and_owning_step() {
      TestFixture f = fixture()
          .retryStepId("s1")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s4")
          .sequence("s1", "s3", "s4")
          .retryStepExecuted(1)
          .build();
      BlueprintRef composite = new BlueprintRef("bp1");
      List<Executable> fullSequenceWithComposite = new ArrayList<>(List.of(
          f.executable("s1"), composite, f.executable("s3"), f.executable("s4")));
      when(f.context().getCurrentSequenceExecutableList()).thenReturn(fullSequenceWithComposite);

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("BlueprintRef:bp1");

      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }

    @Test
    void single_step_allows_an_adjacent_target_even_when_a_composite_follows_the_owning_step() {
      // Sanity check: a composite outside the checked range (after the owning step) must not
      // trigger rejection — only entries strictly between an adjacent SINGLE_STEP target and the
      // owning step matter, and here there are none.
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();
      List<Executable> fullSequenceWithTrailingComposite = new ArrayList<>(List.of(
          f.executable("s1"), f.executable("s2"), f.executable("s3"), new BlueprintRef("bp1")));
      when(f.context().getCurrentSequenceExecutableList()).thenReturn(fullSequenceWithTrailingComposite);

      f.handle();

      verify(executableExecutor, times(1)).execute(f.executable("s2"), f.context());
    }
  }

  @Nested
  class AllowRetryFromPreviousGate {

    @Test
    void rejects_target_carrying_the_bare_default_retry_policy_none() {
      // RetryPolicy.none() is a deliberate fail-closed default — an AGENT/SPAR target that declares
      // no retryPolicy at all (AgentBehaviour's compact constructor substitutes RetryPolicy.none())
      // must be rejected exactly like an explicit allowRetryFromPrevious=false; "undeclared" is never
      // treated as "unrestricted".
      AgentBehaviour undeclared = new AgentBehaviour("agent-1", StepTransition.AUTO, null);
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(undeclared)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("s2")
          .hasMessageContaining("allowRetryFromPrevious=false");

      // Rejected before any mutation: no dispatch, no attempt-counter write.
      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }

    @Test
    void rejects_target_whose_retry_policy_disallows_retry_from_previous() {
      AgentBehaviour forbidding = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, false, 3));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(forbidding)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("s2")
          .hasMessageContaining("allowRetryFromPrevious=false");

      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }

    @Test
    void allows_target_whose_retry_policy_permits_retry_from_previous() {
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 3));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();

      f.handle();

      verify(executableExecutor, times(1)).execute(target, f.context());
    }

    @Test
    void allows_target_step_type_with_no_retry_policy_concept_by_default() {
      // ResourceBehaviour (the default mock executable) carries no RetryPolicy at all — the gate
      // must not invent a new restriction for step types that never had this concept.
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
  }

  @Nested
  class SharedRetryPolicyCeiling {

    @Test
    void rejects_when_the_shared_ceiling_is_already_reached_even_though_the_local_cap_is_not() {
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 1));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();
      // Simulates the target policy's single permitted attempt having already been consumed by a
      // prior WorkflowRuntime.retry() call — the same shared counter this handler reads.
      RetryPolicyAttemptCounter.increment(f.state(), "s2");

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("s2")
          .hasMessageContaining("maxAttempts");

      verifyNoInteractions(executableExecutor);
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
      assertThat(RetryPolicyAttemptCounter.read(f.state(), "s2")).isEqualTo(1);
    }

    @Test
    void permits_and_increments_the_shared_counter_when_the_ceiling_is_not_yet_reached() {
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 2));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();

      f.handle();

      verify(executableExecutor, times(1)).execute(target, f.context());
      assertThat(RetryPolicyAttemptCounter.read(f.state(), "s2")).isEqualTo(1);
    }

    @Test
    void a_lower_local_behaviour_cap_still_wins_over_a_higher_target_policy_cap_via_fallback() {
      // Target policy permits up to 10 attempts (nowhere near exhausted), but RETRY_PREVIOUS's own
      // local cap of 1 is already reached: the fallback runs (not a rejection), and — since the
      // fallback never re-executes the target — the shared counter is left completely untouched.
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 10));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(1)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(1)
          .executableOverride("s2", target)
          .build();

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      verify(executableExecutor, times(1)).execute(fallback, f.context());
      verify(executableExecutor, never()).execute(target, f.context());
      assertThat(RetryPolicyAttemptCounter.read(f.state(), "s2")).isZero();
    }

    @Test
    void fallback_runs_when_the_local_cap_is_reached_even_though_the_shared_ceiling_is_also_already_exhausted() {
      // Regression for the reorder bug: previously validateSharedRetryPolicyCeiling ran
      // unconditionally before the local-cap-vs-fallback decision, so an already-exhausted shared
      // ceiling threw StepExecutionException and wrongly prevented the fallback from ever running —
      // even though the fallback never touches that budget. Local cap=1 (already reached) and the
      // target's shared RetryPolicy cap=1 (already consumed by a prior retry() call) simultaneously:
      // the fallback must still run, the target must never be dispatched, and neither counter changes.
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 1));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(1)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(1)
          .executableOverride("s2", target)
          .build();
      RetryPolicyAttemptCounter.increment(f.state(), "s2");

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      verify(executableExecutor, times(1)).execute(fallback, f.context());
      verify(executableExecutor, never()).execute(target, f.context());
      assertThat(f.attemptCount()).isEqualTo(1);
      assertThat(RetryPolicyAttemptCounter.read(f.state(), "s2")).isEqualTo(1);
      assertThat(f.state().getStepOutputs()).containsKey("s3");
    }

    @Test
    void rejection_leaves_local_counter_context_and_step_state_completely_unchanged() {
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 1));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();
      RetryPolicyAttemptCounter.increment(f.state(), "s2");
      Map<String, Integer> uidsBefore = Map.copyOf(f.state().getStepExecutionUid());

      assertThatThrownBy(f::handle).isInstanceOf(StepExecutionException.class);

      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
      assertThat(f.state().getStepExecutionUid()).isEqualTo(uidsBefore);
      assertThat(f.state().getStepOutputs()).doesNotContainKey("s3");
    }

    @Test
    void the_shared_counter_survives_a_snapshot_reload_round_trip() {
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 3));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s2")
          .withName("s2")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .executableOverride("s2", target)
          .build();

      f.handle();

      WorkflowState reloaded = f.state().snapshot();
      assertThat(RetryPolicyAttemptCounter.read(reloaded, "s2")).isEqualTo(1);
    }

    @Test
    void a_composite_range_rejection_leaves_the_shared_counter_unchanged_even_when_the_target_policy_permits() {
      // The target's own RetryPolicy would permit this retry — the composite-range structural
      // check runs first and rejects before the shared ceiling is ever consulted or mutated.
      AgentBehaviour permitting = new AgentBehaviour("agent-1", StepTransition.AUTO,
          new RetryPolicy(true, true, 5));
      StepDefinition target = StepDefinition.builder()
          .withStepId("s1")
          .withName("s1")
          .withBehaviour(permitting)
          .withContextMapping(ContextMapping.none())
          .build();
      TestFixture f = fixture()
          .retryStepId("s1")
          .maxAttempts(5)
          .retryMode(RetryMode.FROM_STEP)
          .owningStepId("s4")
          .sequence("s1", "s3", "s4")
          .retryStepExecuted(1)
          .executableOverride("s1", target)
          .build();
      BlueprintRef composite = new BlueprintRef("bp1");
      List<Executable> fullSequenceWithComposite = new ArrayList<>(List.of(
          target, composite, f.executable("s3"), f.executable("s4")));
      when(f.context().getCurrentSequenceExecutables()).thenReturn(Map.of(
          "s1", target, "s3", f.executable("s3"), "s4", f.executable("s4")));
      when(f.context().getCurrentSequenceExecutableList()).thenReturn(fullSequenceWithComposite);

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("BlueprintRef:bp1");

      verifyNoInteractions(executableExecutor);
      assertThat(RetryPolicyAttemptCounter.read(f.state(), "s1")).isZero();
      assertThat(f.state().getContext()).doesNotContainKey(f.attemptKey());
    }
  }

  @Nested
  class CompletionMarker {

    @Test
    void records_a_completion_marker_for_the_owning_step_once_dispatch_completes() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();

      f.handle();

      // The owning step (the RETRY_PREVIOUS step itself, "s3") now carries a synthetic output —
      // StepSequenceExecutor's resume-skip guard checks stepOutputs.containsKey(stepId) only.
      assertThat(f.state().getStepOutputs()).containsKey("s3");
    }

    @Test
    void records_the_completion_marker_with_a_fresh_uid_higher_than_the_replayed_target() {
      // The marker's uid must be allocated strictly after the replay so a later rewind whose
      // threshold reaches the replayed target also reaches this marker (see the class-level
      // rationale on markCompletionIfDispatched).
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .build();

      f.handle();

      int replayedUid = f.state().getStepExecutionUid().get("s2");
      int markerUid = f.state().getStepExecutionUid().get("s3");
      assertThat(markerUid).isGreaterThan(replayedUid);
    }

    @Test
    void records_no_completion_marker_when_the_replay_pauses() {
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .outcomeForStep("s2", ExecutionOutcome.PAUSED)
          .build();

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.PAUSED);
      assertThat(f.state().getStepOutputs()).doesNotContainKey("s3");
    }

    @Test
    void records_a_completion_marker_on_the_fallback_path_too() {
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

      assertThat(f.state().getStepOutputs()).containsKey("s3");
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

  /**
   * L4-05 coverage: the staleness-anchor invalidation branches (a surviving in-flight or fallback
   * marker whose dispatch anchor was wiped by an external rewind must be dropped, never honoured)
   * and the composite-fallback arms (fresh dispatch, pause-marker write, and the anchor-less
   * resume that honours the marker as-is).
   */
  @Nested
  class StalenessAnchorsAndCompositeFallback {

    private static final String INFLIGHT_KEY = "__retry_previous_inflight:s3";
    private static final String FALLBACK_INFLIGHT_KEY = "__retry_previous_fallback_inflight:s3";

    @Test
    void stale_replay_marker_with_wiped_anchor_is_dropped_not_resumed() {
      // Marker survived (reserved __ keys are rewind-exempt) but the target's uid — the dispatch
      // anchor — was wiped: the resume path must NOT fire; the entry falls through to the fresh
      // dispatch preconditions, which reject loudly because the target never re-executed.
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(5)
          .retryMode(RetryMode.SINGLE_STEP)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .contextValue(INFLIGHT_KEY,
              new StringContextValue("1", ContextProvenance.SYSTEM_GENERATED))
          .build();

      assertThatThrownBy(f::handle)
          .isInstanceOf(StepExecutionException.class)
          .hasMessageContaining("has not been executed yet");

      assertThat(f.state().getContextValue(INFLIGHT_KEY)).isEmpty();
      assertThat(f.attemptCount()).isZero();
      verify(executableExecutor, never()).execute(f.executable("s2"), f.context());
    }

    @Test
    void stale_replay_marker_with_wiped_anchor_and_exhausted_cap_dispatches_the_fallback_fresh() {
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(2)
          .retryMode(RetryMode.SINGLE_STEP)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .attemptCount(2)
          .contextValue(INFLIGHT_KEY,
              new StringContextValue("1", ContextProvenance.SYSTEM_GENERATED))
          .build();

      ExecutionOutcome outcome = f.handle();

      // A regression honouring the stale marker would resume the replay instead: no fallback
      // execution and no exhaustion event. Both must happen on a fresh dispatch.
      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      verify(executableExecutor, times(1)).execute(fallback, f.context());
      verify(eventRecorder).record(
          eq(RUN_ID), eq("s3"), eq(WorkflowEventType.STEP_RETRIED),
          eq("maxAttempts 2 reached for retryStepId 's2', executing fallback"),
          eq("runtime"));
      assertThat(f.state().getContextValue(INFLIGHT_KEY)).isEmpty();
    }

    @Test
    void stale_fallback_marker_with_wiped_fallback_anchor_re_records_exhaustion_and_dispatches_fresh() {
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(2)
          .retryMode(RetryMode.SINGLE_STEP)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(2)
          .contextValue(FALLBACK_INFLIGHT_KEY,
              new StringContextValue("2", ContextProvenance.SYSTEM_GENERATED))
          .build();

      ExecutionOutcome outcome = f.handle();

      // The fallback step never executed (no uid): the marker is stale. A fresh fallback dispatch
      // re-records the exhaustion event; the resume path would record none.
      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      verify(executableExecutor, times(1)).execute(fallback, f.context());
      verify(eventRecorder).record(
          eq(RUN_ID), eq("s3"), eq(WorkflowEventType.STEP_RETRIED),
          eq("maxAttempts 2 reached for retryStepId 's2', executing fallback"),
          eq("runtime"));
      assertThat(f.state().getContextValue(FALLBACK_INFLIGHT_KEY)).isEmpty();
    }

    @Test
    void interrupted_step_fallback_with_surviving_anchor_resumes_without_a_further_exhaustion_event() {
      Executable fallback = fallbackStep("fallback");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(2)
          .retryMode(RetryMode.SINGLE_STEP)
          .fallback(fallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(2)
          .stepExecuted("fallback", 7, "fallback-out")
          .contextValue(FALLBACK_INFLIGHT_KEY,
              new StringContextValue("2", ContextProvenance.SYSTEM_GENERATED))
          .build();

      ExecutionOutcome outcome = f.handle();

      // The pause's resolution already satisfied the fallback (it bears an output): the dispatch
      // completes without re-executing it and without recording a second exhaustion event.
      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      verify(executableExecutor, never()).execute(fallback, f.context());
      verify(eventRecorder, never()).record(
          any(), any(), eq(WorkflowEventType.STEP_RETRIED), any(), any());
      assertThat(f.state().getContextValue(FALLBACK_INFLIGHT_KEY)).isEmpty();
      assertThat(f.state().getStepOutputs()).containsEntry("s3", "retry-previous:dispatched");
    }

    @Test
    void composite_fallback_marker_is_honoured_as_is_and_resumes_the_composite() {
      Executable compositeFallback = new BlueprintRef("bp-1");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(2)
          .retryMode(RetryMode.SINGLE_STEP)
          .fallback(compositeFallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(2)
          .contextValue(FALLBACK_INFLIGHT_KEY,
              new StringContextValue("2", ContextProvenance.SYSTEM_GENERATED))
          .build();

      ExecutionOutcome outcome = f.handle();

      // A composite fallback carries no single anchor uid, so its marker is honoured as-is: the
      // composite is re-executed as the continuation of the same dispatch — no further exhaustion
      // event, no attempt consumed.
      assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
      verify(executableExecutor, times(1)).execute(compositeFallback, f.context());
      verify(eventRecorder, never()).record(
          any(), any(), eq(WorkflowEventType.STEP_RETRIED), any(), any());
      assertThat(f.attemptCount()).isEqualTo(2);
      assertThat(f.state().getContextValue(FALLBACK_INFLIGHT_KEY)).isEmpty();
    }

    @Test
    void composite_fallback_fresh_dispatch_records_exhaustion_and_writes_the_marker_on_pause() {
      Executable compositeFallback = new BlueprintRef("bp-1");
      TestFixture f = fixture()
          .retryStepId("s2")
          .maxAttempts(2)
          .retryMode(RetryMode.SINGLE_STEP)
          .fallback(compositeFallback)
          .owningStepId("s3")
          .sequence("s1", "s2", "s3")
          .retryStepExecuted(2)
          .attemptCount(2)
          .build();
      when(executableExecutor.execute(compositeFallback, f.context()))
          .thenReturn(ExecutionOutcome.PAUSED);

      ExecutionOutcome outcome = f.handle();

      assertThat(outcome).isEqualTo(ExecutionOutcome.PAUSED);
      verify(eventRecorder).record(
          eq(RUN_ID), eq("s3"), eq(WorkflowEventType.STEP_RETRIED),
          eq("maxAttempts 2 reached for retryStepId 's2', executing fallback"),
          eq("runtime"));
      assertThat(f.state().getContextValue(FALLBACK_INFLIGHT_KEY)).isPresent();
      // No completion marker while paused — the next drive resumes this same dispatch.
      assertThat(f.state().getStepOutputs()).doesNotContainKey("s3");
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
    private final Map<String, Executable> executableOverrides = new LinkedHashMap<>();

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

    /**
     * Overrides the mocked executable resolved for {@code stepId} — e.g. an {@link AgentBehaviour}
     * carrying a {@link RetryPolicy}, for target-policy tests.
     */
    FixtureBuilder executableOverride(String stepId, Executable executable) {
      executableOverrides.put(stepId, executable);
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

      String attemptKey = "__retry_previous_attempts:" + retryStepId;
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
        executables.put(stepId, executableOverrides.getOrDefault(stepId, mockStepExecutable(stepId)));
      }

      ExecutionContext context = mock(ExecutionContext.class);
      when(context.getState()).thenReturn(state);
      when(context.getCurrentSequenceStepIds()).thenReturn(sequence);
      when(context.getCurrentSequenceExecutables()).thenReturn(executables);
      // Default full ordered executable list mirrors "sequence" (plain steps only, no composites) —
      // the same shape production code builds when a sequence contains no BlueprintRef/nested
      // WorkflowDefinition entries. Composite-range-rejection tests re-stub this directly on the
      // built fixture's mocked context (via TestFixture.context()) to inject a composite executable
      // at a specific position.
      List<Executable> fullExecutableList = new ArrayList<>();
      for (String stepId : sequence) {
        fullExecutableList.add(executables.get(stepId));
      }
      when(context.getCurrentSequenceExecutableList()).thenReturn(fullExecutableList);
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
