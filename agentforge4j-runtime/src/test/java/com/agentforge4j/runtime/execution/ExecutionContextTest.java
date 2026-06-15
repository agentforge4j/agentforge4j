package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextTest {

  @Test
  void getActiveWorkflowId_returnsRoot_whenNoWorkflowEntered() {
    ExecutionContext context = new ExecutionContext(state(), workflow("wf-root"), 32);

    assertThat(context.getActiveWorkflowId()).isEqualTo("wf-root");
  }

  @Test
  void getActiveWorkflowId_returnsTopOfStack_whenNestedWorkflowEntered() {
    ExecutionContext context = new ExecutionContext(state(), workflow("wf-root"), 32);

    context.enterWorkflow(workflow("wf-nested"));

    assertThat(context.getActiveWorkflowId()).isEqualTo("wf-nested");
  }

  @Test
  void getActiveWorkflowId_returnsToParent_afterExitingNestedWorkflow() {
    ExecutionContext context = new ExecutionContext(state(), workflow("wf-root"), 32);

    context.enterWorkflow(workflow("wf-nested"));
    context.exitWorkflow();

    assertThat(context.getActiveWorkflowId()).isEqualTo("wf-root");
  }

  private static WorkflowState state() {
    return new WorkflowState("run-1", "wf-root", null, Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static WorkflowDefinition workflow(String id) {
    List<Executable> steps = List.of(StepDefinition.builder()
        .withStepId("s1")
        .withName("s1")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .withContextMapping(ContextMapping.none())
        .build());
    return new WorkflowDefinition(
        id, id, null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), steps);
  }
}
