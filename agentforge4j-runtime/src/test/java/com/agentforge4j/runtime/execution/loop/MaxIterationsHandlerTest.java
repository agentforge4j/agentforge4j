// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MaxIterationsHandlerTest {

  private static final String BLUEPRINT_ID = "bp-max";

  private EventRecorder eventRecorder;
  private MaxIterationsHandler handler;
  private WorkflowState state;
  private ExecutionContext executionContext;
  private BlueprintDefinition blueprint;
  private LoopConfig loopConfig;

  @BeforeEach
  void setUp() {
    eventRecorder = mock(EventRecorder.class);
    handler = new MaxIterationsHandler(eventRecorder,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
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
        "bp",
        new BlueprintBehaviour(null, StepTransition.AUTO),
        List.of(dummyStep()));
    loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 3, MaxIterationsAction.AWAIT_USER);
  }

  @Test
  void await_user_action_returns_paused_and_sets_status_awaiting_input() {
    loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 3, MaxIterationsAction.AWAIT_USER);

    ExecutionOutcome outcome = handler.handle(blueprint, loopConfig, executionContext);

    assertThat(outcome).isEqualTo(ExecutionOutcome.PAUSED);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    verify(eventRecorder).record(
        eq("run-1"),
        eq(BLUEPRINT_ID),
        eq(WorkflowEventType.LOOP_ITERATION_COMPLETED),
        org.mockito.ArgumentMatchers.contains("awaiting user"),
        eq("runtime"));
  }

  @Test
  void fail_action_returns_failed_and_sets_status_failed() {
    loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 3, MaxIterationsAction.FAIL);

    ExecutionOutcome outcome = handler.handle(blueprint, loopConfig, executionContext);

    assertThat(outcome).isEqualTo(ExecutionOutcome.FAILED);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(eventRecorder).record(
        eq("run-1"),
        eq(BLUEPRINT_ID),
        eq(WorkflowEventType.RUN_FAILED),
        payloadCaptor.capture(),
        eq("runtime"));
    assertThat(payloadCaptor.getValue()).contains("maxIterations=3");
  }

  private static StepDefinition dummyStep() {
    return StepDefinition.builder()
        .withStepId("s1")
        .withName("s1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .withContextMapping(ContextMapping.none())
        .build();
  }
}
