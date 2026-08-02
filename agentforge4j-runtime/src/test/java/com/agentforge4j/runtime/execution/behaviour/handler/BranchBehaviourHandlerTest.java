// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.context.ContextProvenance;
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
import com.agentforge4j.core.workflow.step.behaviour.BranchPredicate;
import com.agentforge4j.core.workflow.step.behaviour.BranchPredicateKind;
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
import java.util.Set;

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
        List.of(resourceStep("holder")), List.of());
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
  void matched_branch_with_null_executable_completes_and_does_not_fall_through_to_default() {
    // Exact shape of the epic-implementation "rework-decision" defect: an explicit-null match must
    // complete the branch step, never route to the (non-null) default branch.
    StepDefinition defaultBranch = resourceStep("mark-epic-failed");
    StepDefinition rework = resourceStep("rework");
    Map<String, Executable> branches = new LinkedHashMap<>();
    branches.put("SUCCESS", null);
    branches.put("NEEDS_REWORK", rework);
    putRoute("SUCCESS");
    StepDefinition branchStep = branchStep(branches, defaultBranch);

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
  void selected_branch_step_is_assigned_an_execution_uid() {
    StepDefinition target = resourceStep("target");
    putRoute("go");
    StepDefinition branchStep = branchStep(Map.of("go", target), null);

    handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext);

    // A branch-selected step bypasses the StepSequenceExecutor, so the handler must assign its uid;
    // otherwise a selected AGENT step's command application fails on a null currentStepUid.
    assertThat(state.getStepExecutionUid()).containsKey("target");
    verify(executableExecutor).execute(eq(target), eq(executionContext));
  }

  @Test
  void already_completed_selected_step_is_skipped_on_resume_redrive() {
    StepDefinition target = resourceStep("target");
    putRoute("go");
    StepDefinition branchStep = branchStep(Map.of("go", target), null);
    // Simulate the step having run on a prior drive: a resume re-drives and re-evaluates the branch.
    state.putStepOutput("target", "already-ran");

    assertThat(handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext))
        .isEqualTo(ExecutionOutcome.COMPLETED);
    verify(executableExecutor, times(0)).execute(any(), any());
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

  @Test
  void member_of_predicate_routes_to_its_target() {
    StepDefinition target = resourceStep("tier-target");
    putRoute("STANDARD");
    StepDefinition branchStep = predicateBranchStep(Map.of(),
        List.of(new BranchPredicate(BranchPredicateKind.MEMBER_OF, Set.of("LITE", "STANDARD"), target)),
        null, true);

    handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext);

    verify(executableExecutor).execute(eq(target), eq(executionContext));
  }

  @Test
  void empty_predicate_routes_when_context_key_is_absent() {
    // A StringContextValue cannot be blank, so "empty" is represented by the key being absent.
    StepDefinition target = resourceStep("empty-target");
    StepDefinition branchStep = predicateBranchStep(Map.of(),
        List.of(new BranchPredicate(BranchPredicateKind.EMPTY, Set.of(), target)), null, true);

    handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext);

    verify(executableExecutor).execute(eq(target), eq(executionContext));
  }

  @Test
  void predicate_takes_precedence_over_exact_branch() {
    StepDefinition predicateTarget = resourceStep("predicate-target");
    StepDefinition exactTarget = resourceStep("exact-target");
    putRoute("STANDARD");
    StepDefinition branchStep = predicateBranchStep(Map.of("STANDARD", exactTarget),
        List.of(new BranchPredicate(BranchPredicateKind.MEMBER_OF, Set.of("STANDARD"), predicateTarget)),
        null, false);

    handler.handle(branchStep, (BranchBehaviour) branchStep.behaviour(), executionContext);

    verify(executableExecutor).execute(eq(predicateTarget), eq(executionContext));
    verify(executableExecutor, times(1)).execute(any(), any());
  }

  @Test
  void fail_on_unmatched_fails_the_run_when_nothing_matches() {
    putRoute("UNKNOWN");
    StepDefinition branchStep = predicateBranchStep(Map.of("LITE", resourceStep("lite")), List.of(),
        null, true);

    assertThatThrownBy(() -> handler.handle(branchStep,
        (BranchBehaviour) branchStep.behaviour(), executionContext))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("failOnUnmatched");
    verify(executableExecutor, times(0)).execute(any(), any());
  }

  private void putRoute(String value) {
    state.putContextValue("route", new StringContextValue(value, ContextProvenance.USER_SUPPLIED));
  }

  private static StepDefinition branchStep(Map<String, Executable> branches, Executable defaultBranch) {
    return predicateBranchStep(branches, List.of(), defaultBranch, false);
  }

  private static StepDefinition predicateBranchStep(Map<String, Executable> branches,
      List<BranchPredicate> predicates, Executable defaultBranch, boolean failOnUnmatched) {
    return StepDefinition.builder()
        .withStepId("branch")
        .withName("branch")
        .withBehaviour(new BranchBehaviour("route", branches, predicates, defaultBranch,
            failOnUnmatched))
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
