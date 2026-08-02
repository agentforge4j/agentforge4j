// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.WorkflowTreeWalker;
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorRequirementsTest {

  private final WorkflowValidator validator = new WorkflowValidator(WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH);

  @Test
  void emptyRequirements_pass() {
    WorkflowDefinition workflow = workflow(List.of(), step("review-cv"));

    assertThatCode(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .doesNotThrowAnyException();
  }

  @Test
  void validRequirements_pass() {
    WorkflowDefinition workflow = workflow(
        List.of(
            new WorkflowRequirement("run-access", "rbac_runner_allowed", RequirementScope.WORKFLOW,
                null, null, true, null, ResolutionMode.INSTALL),
            new WorkflowRequirement("cv-review", "rbac_step_action_allowed",
                RequirementScope.STEP_ACTION, "review-cv", "REVIEW", true, null,
                ResolutionMode.INSTALL_OR_RUN_START)),
        step("review-cv"));

    assertThatCode(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .doesNotThrowAnyException();
  }

  @Test
  void duplicateRequirementId_isRejected() {
    WorkflowDefinition workflow = workflow(
        List.of(
            new WorkflowRequirement("dup", "rbac_runner_allowed", RequirementScope.WORKFLOW,
                null, null, true, null, ResolutionMode.INSTALL),
            new WorkflowRequirement("dup", "public_step_access", RequirementScope.STEP,
                "review-cv", null, false, null, ResolutionMode.RUN_START)),
        step("review-cv"));

    assertThatThrownBy(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate requirement id 'dup'");
  }

  @Test
  void unknownTargetStep_isRejected() {
    WorkflowDefinition workflow = workflow(
        List.of(new WorkflowRequirement("cv-review", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "missing-step", "REVIEW", true, null,
            ResolutionMode.INSTALL)),
        step("review-cv"));

    assertThatThrownBy(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targets unknown step 'missing-step'");
  }

  @Test
  void conflictingSameTypeTarget_isRejected() {
    WorkflowDefinition workflow = workflow(
        List.of(
            new WorkflowRequirement("a", "rbac_step_action_allowed", RequirementScope.STEP_ACTION,
                "review-cv", "REVIEW", true, null, ResolutionMode.INSTALL),
            new WorkflowRequirement("b", "rbac_step_action_allowed", RequirementScope.STEP_ACTION,
                "review-cv", "REVIEW", true, null, ResolutionMode.INSTALL)),
        step("review-cv"));

    assertThatThrownBy(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("conflicting requirements");
  }

  @Test
  void differentTypesOnSameTarget_pass() {
    WorkflowDefinition workflow = workflow(
        List.of(
            new WorkflowRequirement("a", "rbac_step_action_allowed", RequirementScope.STEP_ACTION,
                "review-cv", "REVIEW", true, null, ResolutionMode.INSTALL),
            new WorkflowRequirement("b", "public_step_access", RequirementScope.STEP_ACTION,
                "review-cv", "REVIEW", false, null, ResolutionMode.RUN_START)),
        step("review-cv"));

    assertThatCode(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .doesNotThrowAnyException();
  }

  // Regression coverage: a requirement targeting a stepId that only exists inside a blueprint body
  // used to be rejected as "unknown step" because validateWorkflowRequirements's step-id collection
  // explicitly skipped BlueprintRef bodies (the same CL-1 gap fixed for the other five WorkflowValidator
  // walks; this one was deliberately deferred at the time). Now collected via a dedicated,
  // blueprint-body-aware traversal.
  @Test
  void requirementTargetingStepInsideBlueprintBody_isAccepted() {
    BlueprintDefinition blueprintWithTargetStep = blueprint("bp-a", step("bp-step"));
    WorkflowDefinition workflow = workflowWithBlueprints(
        Map.of("bp-a", blueprintWithTargetStep),
        List.of(new WorkflowRequirement("bp-req", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "bp-step", "REVIEW", true, null,
            ResolutionMode.INSTALL)),
        new BlueprintRef("bp-a"));

    assertThatCode(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .doesNotThrowAnyException();
  }

  // Regression coverage: the same step-id collection also never recursed into BranchBehaviour
  // children, so a requirement targeting a step reachable only through a branch was rejected too.
  @Test
  void requirementTargetingStepInsideBranchChild_isAccepted() {
    StepDefinition branchChildStep = step("branch-step");
    BranchBehaviour branch = new BranchBehaviour("routeKey", Map.of("path-a", branchChildStep),
        List.of(), null, false);
    StepDefinition branchStep = StepDefinition.builder()
        .withStepId("b1")
        .withName("Branch")
        .withBehaviour(branch)
        .build();
    WorkflowDefinition workflow = workflow(
        List.of(new WorkflowRequirement("branch-req", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "branch-step", "REVIEW", true, null,
            ResolutionMode.INSTALL)),
        branchStep);

    assertThatCode(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .doesNotThrowAnyException();
  }

  // Regression coverage: a requirement may also target a step reachable only through a
  // RetryPreviousBehaviour's fallback executable, since the shared WorkflowTreeWalker this check now
  // uses descends fallback bodies the same way it descends branch children and blueprint bodies.
  @Test
  void requirementTargetingStepInsideRetryFallback_isAccepted() {
    StepDefinition fallbackStep = step("fallback-step");
    StepDefinition retryStep = StepDefinition.builder()
        .withStepId("r1")
        .withName("Retry")
        .withBehaviour(new RetryPreviousBehaviour("review-cv", RetryMode.FROM_STEP, 2, fallbackStep))
        .build();
    WorkflowDefinition workflow = workflow(
        List.of(new WorkflowRequirement("fallback-req", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "fallback-step", "REVIEW", true, null,
            ResolutionMode.INSTALL)),
        retryStep);

    assertThatCode(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .doesNotThrowAnyException();
  }

  // Confirms the blueprint-body descent fails safe (reports the ordinary "unknown step" outcome, not a
  // crash) when the referenced blueprint itself does not resolve — that failure belongs to
  // validateBlueprintRefs, not this check.
  @Test
  void requirementTargetingStepId_whenBlueprintRefUnresolved_reportsUnknownStepNotCrash() {
    WorkflowDefinition workflow = workflowWithBlueprints(
        Map.of(),
        List.of(new WorkflowRequirement("bp-req", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, "bp-step", "REVIEW", true, null,
            ResolutionMode.INSTALL)),
        new BlueprintRef("ghost-bp"));

    assertThatThrownBy(() -> validator.validateRequirements(Map.of(workflow.id(), workflow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targets unknown step 'bp-step'");
  }

  private static BlueprintDefinition blueprint(String blueprintId, Executable... steps) {
    return new BlueprintDefinition(blueprintId, blueprintId,
        new BlueprintBehaviour(null, StepTransition.AUTO), List.of(steps));
  }

  private static StepDefinition step(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .build();
  }

  private static WorkflowDefinition workflow(List<WorkflowRequirement> requirements,
      Executable... steps) {
    return new WorkflowDefinition("recruitment", "Recruitment", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(steps),
        requirements);
  }

  private static WorkflowDefinition workflowWithBlueprints(
      Map<String, BlueprintDefinition> blueprints, List<WorkflowRequirement> requirements,
      Executable... steps) {
    return new WorkflowDefinition("recruitment", "Recruitment", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, List.of(steps),
        requirements);
  }
}
