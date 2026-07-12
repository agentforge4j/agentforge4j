// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.LoopEvaluator;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoopCancellationTest {

  private static final String BLUEPRINT_ID = "bp-loop";

  @Test
  void cancelled_mid_iteration_clears_loop_cursor_for_fixed_count() {
    Fixture f = fixture();
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(
        f.stepSequenceExecutor(), f.eventRecorder(), f.maxIterationsHandler());

    assertCancelledClearsCursor(strategy, f);
  }

  @Test
  void cancelled_mid_iteration_clears_loop_cursor_for_evaluator() {
    Fixture f = fixture();
    LoopEvaluator evaluator = mock(LoopEvaluator.class);
    when(evaluator.shouldTerminate(anyString(), anyInt(), any())).thenReturn(false);
    EvaluatorLoopStrategy strategy = new EvaluatorLoopStrategy(
        f.stepSequenceExecutor(), f.eventRecorder(), f.maxIterationsHandler(), evaluator);

    assertCancelledClearsCursor(strategy, f);
  }

  @Test
  void cancelled_mid_iteration_clears_loop_cursor_for_agent_signal() {
    Fixture f = fixture();
    AgentSignalLoopStrategy strategy = new AgentSignalLoopStrategy(
        f.stepSequenceExecutor(), f.eventRecorder(), f.maxIterationsHandler());

    assertCancelledClearsCursor(strategy, f);
  }

  @Test
  void true_pause_preserves_loop_cursor() {
    Fixture f = fixture();
    when(f.stepSequenceExecutor().executeAll(anyList(), any()))
        .thenReturn(ExecutionOutcome.PAUSED);
    FixedCountLoopStrategy strategy = new FixedCountLoopStrategy(
        f.stepSequenceExecutor(), f.eventRecorder(), f.maxIterationsHandler());

    assertThat(strategy.iterate(f.blueprint(), f.loopConfig(), f.executionContext()))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(f.state().getLoopIterationCursor(BLUEPRINT_ID)).isEqualTo(2);
  }

  private static void assertCancelledClearsCursor(AbstractLoopStrategy strategy, Fixture f) {
    assertThat(strategy.iterate(f.blueprint(), f.loopConfig(), f.executionContext()))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(f.state().getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  private static Fixture fixture() {
    EventRecorder eventRecorder = mock(EventRecorder.class);
    MaxIterationsHandler maxIterationsHandler = new MaxIterationsHandler(eventRecorder,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    WorkflowState state = new WorkflowState("run-1", "wf-1", null,
        Instant.parse("2026-05-01T12:00:00Z"));
    state.setLoopIterationCursor(BLUEPRINT_ID, 2);
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
        List.of(dummyStep()), List.of());
    ExecutionContext executionContext = new ExecutionContext(state, workflow, 32);
    BlueprintDefinition blueprint = new BlueprintDefinition(
        BLUEPRINT_ID,
        "loop blueprint",
        new BlueprintBehaviour(LoopConfig.withDefaults(
            LoopTerminationStrategy.FIXED_COUNT, null, null, 3, null),
            StepTransition.AUTO),
        List.of(dummyStep()));
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenAnswer(inv -> {
          state.setStatus(WorkflowStatus.CANCELLED);
          return ExecutionOutcome.COMPLETED;
        });
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 3, null);
    return new Fixture(
        eventRecorder, maxIterationsHandler, stepSequenceExecutor, state, executionContext,
        blueprint, loopConfig);
  }

  private static StepDefinition dummyStep() {
    return StepDefinition.builder()
        .withStepId("dummy")
        .withName("dummy")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "dummy.out", StepTransition.AUTO))
        .build();
  }

  private record Fixture(
      EventRecorder eventRecorder,
      MaxIterationsHandler maxIterationsHandler,
      StepSequenceExecutor stepSequenceExecutor,
      WorkflowState state,
      ExecutionContext executionContext,
      BlueprintDefinition blueprint,
      LoopConfig loopConfig) {
  }
}
