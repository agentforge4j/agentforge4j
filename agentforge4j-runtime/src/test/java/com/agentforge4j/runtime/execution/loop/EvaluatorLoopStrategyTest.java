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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluatorLoopStrategyTest {

  private static final String BLUEPRINT_ID = "bp-eval";

  private EventRecorder eventRecorder;
  private MaxIterationsHandler maxIterationsHandler;
  private StepSequenceExecutor stepSequenceExecutor;
  private LoopEvaluator loopEvaluator;
  private EvaluatorLoopStrategy strategy;
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
    loopEvaluator = mock(LoopEvaluator.class);
    strategy = new EvaluatorLoopStrategy(
        stepSequenceExecutor, eventRecorder, maxIterationsHandler, loopEvaluator);
    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
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
    loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.EVALUATOR, null, "eval-agent", 5, null);
    blueprint = new BlueprintDefinition(
        BLUEPRINT_ID,
        "eval bp",
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO),
        List.of(dummyStep()));
  }

  @Test
  void terminates_on_evaluator_signal() {
    AtomicInteger iteration = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    when(loopEvaluator.shouldTerminate(anyString(), anyInt(), any())).thenAnswer(inv -> {
      return iteration.incrementAndGet() >= 2;
    });

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  @Test
  void runs_to_max_iterations_when_evaluator_never_signals() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    when(loopEvaluator.shouldTerminate(anyString(), anyInt(), any())).thenReturn(false);

    LoopConfig tight = LoopConfig.withDefaults(
        LoopTerminationStrategy.EVALUATOR, null, "eval-agent", 2, null);

    assertThat(strategy.iterate(blueprint, tight, executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
  }

  @Test
  void iteration_pause_preserves_cursor() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.PAUSED);

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isEqualTo(1);
  }

  @Test
  void iteration_failure_clears_cursor() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.FAILED);

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.FAILED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  @Test
  void resume_starts_from_stored_cursor() {
    state.setLoopIterationCursor(BLUEPRINT_ID, 3);
    AtomicInteger seenIteration = new AtomicInteger(0);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    when(loopEvaluator.shouldTerminate(anyString(), anyInt(), any())).thenAnswer(inv -> {
      seenIteration.set(inv.getArgument(1));
      return ((Integer) inv.getArgument(1)) == 5;
    });

    LoopConfig tight = LoopConfig.withDefaults(
        LoopTerminationStrategy.EVALUATOR, null, "eval-agent", 5, null);
    strategy.iterate(blueprint, tight, executionContext);

    assertThat(seenIteration.get()).isEqualTo(5);
  }

  @Test
  void cancelled_mid_iteration_clears_cursor() {
    state.setLoopIterationCursor(BLUEPRINT_ID, 2);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(inv -> {
      state.setStatus(WorkflowStatus.CANCELLED);
      return ExecutionOutcome.COMPLETED;
    });

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
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
