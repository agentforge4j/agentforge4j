package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowExecutorTest {

  private WorkflowExecutor workflowExecutor;
  private StepSequenceExecutor stepSequenceExecutor;
  private WorkflowDefinition root;
  private WorkflowDefinition nested;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    workflowExecutor = new WorkflowExecutor();
    stepSequenceExecutor = mock(StepSequenceExecutor.class);
    workflowExecutor.setStepSequenceExecutor(stepSequenceExecutor);

    root = workflow("wf-root", List.of(dummyStep("root-step")));
    nested = workflow("wf-nested", List.of(dummyStep("nested-step")));
    WorkflowState state = new WorkflowState("run-1", root.id(), null,
        Instant.parse("2026-05-01T00:00:00Z"));
    executionContext = new ExecutionContext(state, root, 32);
    executionContext.enterWorkflow(root);
  }

  @Test
  void pushes_nested_workflow_onto_active_stack_before_delegating() {
    AtomicReference<WorkflowDefinition> activeAtInvocation = new AtomicReference<>();
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(inv -> {
      activeAtInvocation.set(executionContext.getActiveWorkflowStack().peek());
      return ExecutionOutcome.COMPLETED;
    });

    workflowExecutor.execute(nested, executionContext);

    assertThat(activeAtInvocation.get()).isEqualTo(nested);
  }

  @Test
  void pops_nested_workflow_after_completed_outcome() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);

    workflowExecutor.execute(nested, executionContext);

    assertThat(executionContext.getActiveWorkflowStack()).containsExactly(root);
  }

  @Test
  void pops_nested_workflow_after_paused_outcome() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.PAUSED);

    workflowExecutor.execute(nested, executionContext);

    assertThat(executionContext.getActiveWorkflowStack()).containsExactly(root);
  }

  @Test
  void pops_nested_workflow_after_failed_outcome() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.FAILED);

    workflowExecutor.execute(nested, executionContext);

    assertThat(executionContext.getActiveWorkflowStack()).containsExactly(root);
  }

  @Test
  void pops_nested_workflow_when_body_throws() {
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenThrow(new RuntimeException("body failed"));

    assertThatThrownBy(() -> workflowExecutor.execute(nested, executionContext))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("body failed");
    assertThat(executionContext.getActiveWorkflowStack()).containsExactly(root);
  }

  @Test
  void unwired_execute_throws() {
    WorkflowExecutor unwired = new WorkflowExecutor();
    assertThatThrownBy(() -> unwired.execute(nested, executionContext))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("setStepSequenceExecutor");
  }

  private static StepDefinition dummyStep(String stepId) {
    return new StepDefinition(
        stepId,
        stepId,
        new ResourceBehaviour("/examples/sample.txt", stepId + ".out", StepTransition.AUTO),
        ContextMapping.none(),
        null,
        null);
  }

  private static WorkflowDefinition workflow(String id,
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
        Map.of(),
        steps);
  }
}
