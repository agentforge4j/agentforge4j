// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.loop.FixedCountLoopStrategy;
import com.agentforge4j.runtime.execution.loop.LoopStrategy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlueprintExecutorTest {

  private static final String BP_ID = "bp1";

  private BlueprintExecutor blueprintExecutor;
  private StepSequenceExecutor stepSequenceExecutor;
  private FixedCountLoopStrategy fixedCountLoopStrategy;
  private LoopStrategy agentSignalLoopStrategy;
  private LoopStrategy forEachLoopStrategy;
  private WorkflowState state;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    blueprintExecutor = new BlueprintExecutor();
    stepSequenceExecutor = mock(StepSequenceExecutor.class);
    fixedCountLoopStrategy = mock(FixedCountLoopStrategy.class);
    when(fixedCountLoopStrategy.strategy()).thenReturn(LoopTerminationStrategy.FIXED_COUNT);
    agentSignalLoopStrategy = mock(LoopStrategy.class);
    when(agentSignalLoopStrategy.strategy()).thenReturn(LoopTerminationStrategy.AGENT_SIGNAL);
    forEachLoopStrategy = mock(LoopStrategy.class);
    when(forEachLoopStrategy.strategy()).thenReturn(LoopTerminationStrategy.FOR_EACH);
    blueprintExecutor.setStepSequenceExecutor(stepSequenceExecutor);
    blueprintExecutor.setLoopStrategies(
        List.of(fixedCountLoopStrategy, agentSignalLoopStrategy, forEachLoopStrategy));
    blueprintExecutor.setTransitionGate(new TransitionGate(mock(EventRecorder.class)));
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);

    state = new WorkflowState("run-1", "wf-root", null, Instant.parse("2026-05-01T00:00:00Z"));
  }

  @Test
  void resolves_blueprint_from_innermost_active_workflow() {
    StepDefinition rootStep = resourceStep("step-a", "root.executed");
    StepDefinition nestedStep = resourceStep("step-b", "nested.executed");
    BlueprintDefinition rootBp = blueprint(BP_ID, List.of(rootStep), null);
    BlueprintDefinition nestedBp = blueprint(BP_ID, List.of(nestedStep), null);

    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, rootBp),
        List.of(resourceStep("root-holder", "holder")));
    WorkflowDefinition nested = workflow("wf-nested", Map.of(BP_ID, nestedBp),
        List.of(resourceStep("nested-holder", "holder")));
    executionContext = contextWithStack(root, nested);

    blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    ArgumentCaptor<List> stepsCaptor = ArgumentCaptor.forClass(List.class);
    verify(stepSequenceExecutor).executeAll(stepsCaptor.capture(), eq(executionContext));
    assertThat(stepsCaptor.getValue()).containsExactly(nestedStep);
  }

  @Test
  void empty_active_workflow_stack_throws() {
    WorkflowDefinition root = workflow("wf-root", Map.of(),
        List.of(resourceStep("holder", "holder")));
    executionContext = new ExecutionContext(state, root, 32);

    assertThatThrownBy(() -> blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no active workflow on stack");
  }

  @Test
  void unknown_blueprint_id_throws_with_workflow_id() {
    WorkflowDefinition root = workflow("wf-root", Map.of(),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);

    assertThatThrownBy(() -> blueprintExecutor.execute(new BlueprintRef("missing"), executionContext))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing")
        .hasMessageContaining("wf-root");
  }

  @Test
  void non_looped_blueprint_delegates_to_step_sequence_executor() {
    StepDefinition body = resourceStep("body", "ctx");
    BlueprintDefinition bp = blueprint(BP_ID, List.of(body), null);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);

    blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    verify(stepSequenceExecutor).executeAll(eq(bp.steps()), eq(executionContext));
    verify(fixedCountLoopStrategy, never()).iterate(any(), any(), any());
  }

  @Test
  void looped_blueprint_delegates_to_matching_loop_strategy() {
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 2, null);
    BlueprintDefinition bp = blueprint(BP_ID, List.of(resourceStep("body", "ctx")), loopConfig);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);
    when(fixedCountLoopStrategy.iterate(eq(bp), eq(loopConfig), eq(executionContext)))
        .thenReturn(ExecutionOutcome.COMPLETED);

    blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    verify(fixedCountLoopStrategy).iterate(bp, loopConfig, executionContext);
    verify(stepSequenceExecutor, never()).executeAll(anyList(), any());
  }

  @Test
  void signal_loop_completed_is_skipped_on_resume() {
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 5, null);
    BlueprintDefinition bp = blueprint(BP_ID, List.of(resourceStep("body", "ctx")), loopConfig);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);
    state.markLoopCompleted(BP_ID, 3); // completed on a prior drive

    ExecutionOutcome outcome = blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    verify(agentSignalLoopStrategy, never()).iterate(any(), any(), any());
  }

  @Test
  void signal_loop_runs_when_marker_absent_eg_after_a_rewind_cleared_it() {
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 5, null);
    BlueprintDefinition bp = blueprint(BP_ID, List.of(resourceStep("body", "ctx")), loopConfig);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);
    // No marker (e.g. a retry/rewind cleared it via clearEntriesFromUid) — the loop must run again.
    when(agentSignalLoopStrategy.iterate(eq(bp), eq(loopConfig), eq(executionContext)))
        .thenReturn(ExecutionOutcome.COMPLETED);

    blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    verify(agentSignalLoopStrategy).iterate(bp, loopConfig, executionContext);
  }

  @Test
  void signal_loop_completion_is_recorded_keyed_on_the_body_completion_uid() {
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 5, null);
    BlueprintDefinition bp = blueprint(BP_ID, List.of(resourceStep("body", "ctx")), loopConfig);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);
    state.putStepExecutionUid("body", 7); // body completed at uid 7
    when(agentSignalLoopStrategy.iterate(eq(bp), eq(loopConfig), eq(executionContext)))
        .thenReturn(ExecutionOutcome.COMPLETED);

    blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    assertThat(state.isLoopCompleted(BP_ID)).isTrue();
    assertThat(state.getCompletedLoopBlueprintUids()).containsEntry(BP_ID, 7);
  }

  @Test
  void for_each_loop_is_never_skipped_so_its_list_change_fingerprint_still_applies() {
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.FOR_EACH, "items", null, 50, null);
    BlueprintDefinition bp = blueprint(BP_ID, List.of(resourceStep("body", "ctx")), loopConfig);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);
    // Even with a completion marker present, a FOR_EACH loop must still re-enter its own strategy on
    // a re-drive so its fingerprint / list-change check runs; it is never skipped here.
    state.markLoopCompleted(BP_ID, 3);
    when(forEachLoopStrategy.iterate(eq(bp), eq(loopConfig), eq(executionContext)))
        .thenReturn(ExecutionOutcome.COMPLETED);

    blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext);

    verify(forEachLoopStrategy).iterate(bp, loopConfig, executionContext);
  }

  @Test
  void missing_strategy_registration_throws() {
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.EVALUATOR, null, "eval-agent", 2, null);
    BlueprintDefinition bp = blueprint(BP_ID, List.of(resourceStep("body", "ctx")), loopConfig);
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID, bp),
        List.of(resourceStep("holder", "holder")));
    executionContext = contextWithStack(root);

    assertThatThrownBy(() -> blueprintExecutor.execute(new BlueprintRef(BP_ID), executionContext))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("EVALUATOR");
  }

  @Test
  void setter_guards_double_set_loop_strategies_throws() {
    assertThatThrownBy(() -> blueprintExecutor.setLoopStrategies(List.of(fixedCountLoopStrategy)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LoopStrategies already set");
  }

  @Test
  void setter_guards_double_set_step_sequence_executor_throws() {
    BlueprintExecutor fresh = new BlueprintExecutor();
    fresh.setStepSequenceExecutor(stepSequenceExecutor);

    assertThatThrownBy(() -> fresh.setStepSequenceExecutor(stepSequenceExecutor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StepSequenceExecutor already set");
  }

  @Test
  void unwired_execute_before_setters_throws() {
    BlueprintExecutor unwired = new BlueprintExecutor();
    WorkflowDefinition root = workflow("wf-root", Map.of(BP_ID,
            blueprint(BP_ID, List.of(resourceStep("s", "k")), null)),
        List.of(resourceStep("holder", "holder")));
    ExecutionContext ctx = contextWithStack(root);

    assertThatThrownBy(() -> unwired.execute(new BlueprintRef(BP_ID), ctx))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("setStepSequenceExecutor");
  }

  private ExecutionContext contextWithStack(WorkflowDefinition... workflows) {
    WorkflowDefinition root = workflows[0];
    ExecutionContext ctx = new ExecutionContext(state, root, 32);
    Deque<WorkflowDefinition> stack = new ArrayDeque<>();
    for (int i = workflows.length - 1; i >= 0; i--) {
      stack.push(workflows[i]);
    }
    for (WorkflowDefinition wf : stack) {
      ctx.enterWorkflow(wf);
    }
    return ctx;
  }

  private static BlueprintDefinition blueprint(String id, List<com.agentforge4j.core.workflow.Executable> steps,
      LoopConfig loopConfig) {
    return new BlueprintDefinition(
        id,
        id,
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO),
        steps);
  }

  private static StepDefinition resourceStep(String stepId, String contextKey) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", contextKey, StepTransition.AUTO))
        .withContextMapping(ContextMapping.none())
        .build();
  }

  private static WorkflowDefinition workflow(String id,
      Map<String, BlueprintDefinition> blueprints,
      List<com.agentforge4j.core.workflow.Executable> steps) {
    return new WorkflowDefinition(
        id,
        id,
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        blueprints,
        steps,
        List.of(),
        List.of());
  }
}
