// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.spi.governance.WasteSignalPolicy;
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
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSignalLoopStrategyTest {

  private static final String BLUEPRINT_ID = "bp-agent-signal";

  private EventRecorder eventRecorder;
  private MaxIterationsHandler maxIterationsHandler;
  private StepSequenceExecutor stepSequenceExecutor;
  private AgentSignalLoopStrategy strategy;
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
    strategy = new AgentSignalLoopStrategy(stepSequenceExecutor, eventRecorder,
        maxIterationsHandler, new ObjectMapper(), WasteSignalPolicy.NO_OP);
    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(dummyStep()), List.of(), List.of());
    executionContext = new ExecutionContext(state, workflow, 32);
    loopConfig = agentSignalConfig(8, MaxIterationsAction.AWAIT_USER);
    blueprint = new BlueprintDefinition(
        BLUEPRINT_ID, "agent signal bp",
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO),
        List.of(dummyStep()));
  }

  @Test
  void terminates_cleanly_when_agent_signals_completion_on_first_iteration() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(inv -> {
      executionContext.setAgentCompletionSignalled(true);
      return ExecutionOutcome.COMPLETED;
    });

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
    verify(stepSequenceExecutor, times(1)).executeAll(anyList(), any());
  }

  @Test
  void continues_iterating_until_agent_signals_completion() {
    AtomicInteger calls = new AtomicInteger();
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(inv -> {
      // First iteration: no signal (continue). Second iteration: signal completion.
      executionContext.setAgentCompletionSignalled(calls.incrementAndGet() >= 2);
      return ExecutionOutcome.COMPLETED;
    });

    assertThat(strategy.iterate(blueprint, loopConfig, executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(calls.get()).isEqualTo(2);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  @Test
  void awaits_user_at_max_iterations_when_agent_never_signals() {
    // Missing completion signal: the loop bounds on maxIterations and the handler pauses.
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);

    LoopConfig tight = agentSignalConfig(3, MaxIterationsAction.AWAIT_USER);

    assertThat(strategy.iterate(blueprint, tight, executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    verify(stepSequenceExecutor, times(3)).executeAll(anyList(), any());
  }

  @Test
  void fails_at_max_iterations_when_configured_to_fail() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);

    LoopConfig failing = agentSignalConfig(2, MaxIterationsAction.FAIL);

    assertThat(strategy.iterate(blueprint, failing, executionContext))
        .isEqualTo(ExecutionOutcome.FAILED);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  @Test
  void iteration_pause_preserves_cursor_for_resume() {
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
  void resume_starts_from_stored_cursor_then_completes_on_signal() {
    state.setLoopIterationCursor(BLUEPRINT_ID, 3);
    AtomicInteger firstSeen = new AtomicInteger(-1);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(inv -> {
      int iteration = state.getLoopIterationCursor(BLUEPRINT_ID);
      firstSeen.compareAndSet(-1, iteration);
      executionContext.setAgentCompletionSignalled(iteration == 5);
      return ExecutionOutcome.COMPLETED;
    });

    assertThat(strategy.iterate(blueprint, agentSignalConfig(8, MaxIterationsAction.AWAIT_USER),
        executionContext)).isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(firstSeen.get()).isEqualTo(3);
    assertThat(state.getLoopIterationCursor(BLUEPRINT_ID)).isZero();
  }

  private static LoopConfig agentSignalConfig(int maxIterations, MaxIterationsAction action) {
    return LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, maxIterations, action);
  }

  private static StepDefinition dummyStep() {
    return StepDefinition.builder()
        .withStepId("s1")
        .withName("s1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .build();
  }
}
