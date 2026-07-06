// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorRequirementsTest {

  private final WorkflowValidator validator = new WorkflowValidator();

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
        requirements, List.of());
  }
}
