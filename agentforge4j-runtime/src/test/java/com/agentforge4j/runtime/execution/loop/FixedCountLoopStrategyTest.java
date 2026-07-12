// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedCountLoopStrategyTest {

  private static final String BLUEPRINT_ID = "bp-fixed-count";

  private EventRecorder eventRecorder;
  private MaxIterationsHandler maxIterationsHandler;
  private StepSequenceExecutor stepSequenceExecutor;
  private FixedCountLoopStrategy strategy;
  private WorkflowState state;
  private ExecutionContext executionContext;
  private BlueprintDefinition blueprint;
  private LoopConfig loopConfig;

  @BeforeEach
  void setUp() {
    eventRecorder = mock(EventRecorder.class);
    maxIterationsHandler = new MaxIterationsHandler(eventRecorder,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    stepSequenceExecutor = mock(StepSequenceExecutor.class);
    strategy = new FixedCountLoopStrategy(stepSequenceExecutor, eventRecorder, maxIterationsHandler);
    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    WorkflowDefinition workflow = WorkflowDefinition.builder()
        .withId("wf-1")
        .withName("wf-1")
        .withSource(WorkflowSource.CUSTOM)
        .withLifecycle(WorkflowLifecycle.ACTIVE)
        .withSteps(List.of(dummyStep()))
        .build();
    executionContext = new ExecutionContext(state, workflow, 32);
    loopConfig = LoopConfig.withDefaults(LoopTerminationStrategy.FIXED_COUNT, null, null, 3, null);
    blueprint = new BlueprintDefinition(
        BLUEPRINT_ID, "fixed count bp",
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO),
        List.of(dummyStep()));
  }

  @Test
  void retry_previous_targeting_the_bodys_own_first_step_does_not_wipe_a_later_steps_output_clearing() {
    // Regression for WorkflowState.clearEntriesFromUid's loop-cursor sweep (added to fix a stale
    // cursor surviving an EXTERNAL retry) firing on an INTERNAL rewind instead. A RETRY_PREVIOUS step
    // inside a loop body, retrying that body's own first-executed step (stepA), has a retryUid that is
    // always exactly equal to the loop's own recorded body-start-uid — the same numeric signature as an
    // external rewind landing exactly on the loop. clearStepEntriesFromUid always clears stepA's own
    // output as a direct side effect of the retry itself (retryUid == stepA's own uid), so stepA
    // self-heals regardless of this bug — the defect instead lands on a later, unrelated step (stepC)
    // that already completed normally *after* the retry within the same iteration: without excluding
    // the loop's own blueprint id (via ExecutionContext.activeLoopBlueprintIds, pushed/popped by
    // AbstractLoopStrategy.executeIteration around the body call), clearEntriesFromUid's loop-cursor
    // sweep wipes the still-active iteration's own cursor/body-start-uid out from under it; the next
    // iteration's markLoopIterationStart then reads "no previous body-start-uid" as "this is genuinely
    // iteration one" and skips clearing stepC's stale output, so stepC stays wrongly skipped (via
    // StepSequenceExecutor's real resume-skip guard) for the rest of the run.
    //
    // The mocked body below reproduces exactly the state transitions a real skip-guarded step (an
    // AGENT/SPAR step, the only behaviour types that ever call WorkflowState.putStepOutput) plus a
    // RETRY_PREVIOUS step targeting it would produce — without needing the full behaviour-handler
    // stack, so this test exercises the real WorkflowState/ExecutionContext/AbstractLoopStrategy
    // production code under test in isolation from unrelated step-execution machinery.
    AtomicInteger stepCExecutions = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      WorkflowState liveState = context.getState();
      if (!liveState.getStepOutputs().containsKey("stepA")) {
        int uidA = context.allocateStepSequenceUid();
        liveState.putStepExecutionUid("stepA", uidA);
        liveState.putStepOutput("stepA", "a-out");
        // Simulates a RETRY_PREVIOUS step immediately after stepA in the same body, retrying stepA —
        // its own first-executed step. retryUid is exactly stepA's own just-allocated uid, matching
        // RetryPreviousBehaviourHandler's real
        // state.clearEntriesFromUid(retryUid, activeLoopBlueprintIds()) call.
        liveState.clearEntriesFromUid(uidA, context.activeLoopBlueprintIds());
      }
      // stepC: an unrelated, later step in the same body that is never itself retried.
      if (!liveState.getStepOutputs().containsKey("stepC")) {
        int uidC = context.allocateStepSequenceUid();
        liveState.putStepExecutionUid("stepC", uidC);
        liveState.putStepOutput("stepC", "c-out");
        stepCExecutions.incrementAndGet();
      }
      return ExecutionOutcome.COMPLETED;
    });

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);

    // Pre-fix, only iteration 1 genuinely executed stepC; the loop-cursor sweep firing on the
    // in-body retry wiped the still-active iteration's own bookkeeping, so markLoopIterationStart
    // never cleared iteration 1's stale stepC output and every later iteration silently skipped it.
    assertThat(stepCExecutions.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  private static StepDefinition dummyStep() {
    return StepDefinition.builder()
        .withStepId("s1")
        .withName("s1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .build();
  }
}
