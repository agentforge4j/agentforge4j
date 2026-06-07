package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchBehaviourHandlerTest {

  private EventRecorder eventRecorder;
  private ExecutableExecutor executableExecutor;
  private BranchBehaviourHandler handler;
  private WorkflowState state;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    eventRecorder = mock(EventRecorder.class);
    executableExecutor = mock(ExecutableExecutor.class);
    handler = new BranchBehaviourHandler(eventRecorder);
    handler.setExecutableExecutor(executableExecutor);
    when(executableExecutor.execute(any(Executable.class), any()))
        .thenReturn(ExecutionOutcome.COMPLETED);

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
        List.of(resourceStep("holder")));
    state = new WorkflowState("run-1", workflow.id(), null, Instant.parse("2026-05-01T00:00:00Z"));
    executionContext = new ExecutionContext(state, workflow, 32);
    executionContext.enterWorkflow(workflow);
  }

  @Test
  void selected_branch_executes_target() {
    StepDefinition target = resourceStep("target");
    putRoute("go");
    StepDefinition branchStep = branchStep(Map.of("go", target), null);

    assertThat(handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    verify(executableExecutor).execute(eq(target), eq(executionContext));
  }

  @Test
  void no_branch_matches_executes_default() {
    StepDefinition defaultBranch = resourceStep("default-branch");
    putRoute("miss");
    StepDefinition branchStep = branchStep(Map.of("go", resourceStep("never")), defaultBranch);

    handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext);

    verify(executableExecutor).execute(eq(defaultBranch), eq(executionContext));
  }

  @Test
  void no_match_no_default_completes_without_executing_branch_body() {
    putRoute("miss");
    StepDefinition branchStep = branchStep(Map.of("go", resourceStep("never")), null);

    assertThat(handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    verify(executableExecutor, times(0)).execute(any(), any());
  }

  @Test
  void first_matching_branch_wins() {
    StepDefinition first = resourceStep("first");
    StepDefinition second = resourceStep("second");
    putRoute("dup");
    Map<String, Executable> branches = new LinkedHashMap<>();
    branches.put("dup", first);
    branches.put("dup2", second);
    StepDefinition branchStep = branchStep(branches, null);

    handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext);

    verify(executableExecutor).execute(eq(first), eq(executionContext));
    verify(executableExecutor, times(1)).execute(any(), any());
  }

  @Test
  void branch_outcome_is_propagated() {
    StepDefinition target = resourceStep("target");
    putRoute("go");
    StepDefinition branchStep = branchStep(Map.of("go", target), null);
    when(executableExecutor.execute(eq(target), any())).thenReturn(ExecutionOutcome.PAUSED);

    assertThat(handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext))
        .isEqualTo(ExecutionOutcome.PAUSED);
  }

  @Test
  void missing_key_throws() {
    StepDefinition branchStep = branchStep(Map.of("go", resourceStep("x")), null);

    assertThatThrownBy(() -> handler.handle(branchStep,
        (BranchBehaviour) branchStep.behaviour(), executionContext))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("route");
  }

  private void putRoute(String value) {
    state.putContextValue("route", new StringContextValue(value));
  }

  private static StepDefinition branchStep(Map<String, Executable> branches, Executable defaultBranch) {
    return StepDefinition.builder()
        .withStepId("branch")
        .withName("branch")
        .withBehaviour(new BranchBehaviour("route", branches, defaultBranch))
        .withContextMapping(ContextMapping.none())
        .build();
  }

  private static StepDefinition resourceStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", stepId + ".out", StepTransition.AUTO))
        .withContextMapping(ContextMapping.none())
        .build();
  }
}
