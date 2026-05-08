package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Runs non-throwing workflow draft validation and collects failures into a report.
 */
@RequiredArgsConstructor
public final class WorkflowDraftValidator {

  private final WorkflowValidator validator;

  /**
   * Validates draft workflows against global agents and cross-reference rules.
   *
   * @param workflows workflows to validate
   * @param globalAgents agents available to workflow references
   * @return validation report containing any errors captured during checks
   */
  public ValidationReport validate(Map<String, WorkflowDefinition> workflows,
                                   Map<String, AgentDefinition> globalAgents) {
    List<ValidationError> errors = new ArrayList<>();
    runValidation(errors, "validateAgentRefs", () -> validator.validateAgentRefs(workflows, globalAgents));
    runValidation(errors, "validateWorkflowRefs", () -> validator.validateWorkflowRefs(workflows));
    runValidation(errors, "validateBlueprintRefs", () -> validator.validateBlueprintRefs(workflows));
    runValidation(errors, "validateArtifactRefs", () -> validator.validateArtifactRefs(workflows));
    runValidation(errors, "validateCircularRefs", () -> validator.validateCircularRefs(workflows));
    runValidation(errors, "validateRetryStepRefs", () -> validator.validateRetryStepRefs(workflows));
    return new ValidationReport(errors);
  }

  private static void runValidation(List<ValidationError> errors, String code, Runnable runner) {
    try {
      runner.run();
    } catch (RuntimeException exception) {
      errors.add(new ValidationError(code, exception.getMessage()));
    }
  }
}
