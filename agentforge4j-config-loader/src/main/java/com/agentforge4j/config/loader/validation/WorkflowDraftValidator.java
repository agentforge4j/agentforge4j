// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.spi.contextpack.ContextPack;
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
   * @param workflows        workflows to validate
   * @param globalAgents     agents available to workflow references
   * @param loadedPacksByName the context packs actually loaded for this assembly, keyed by name; empty when
   *                         none are configured
   * @return validation report containing any errors captured during checks
   */
  public ValidationReport validate(Map<String, WorkflowDefinition> workflows,
                                   Map<String, AgentDefinition> globalAgents,
                                   Map<String, ContextPack> loadedPacksByName) {
    List<ValidationError> errors = new ArrayList<>();
    for (ValidationCheck check : ValidationCheck.suite(validator, loadedPacksByName)) {
      if (check.draftExempt()) {
        continue;
      }
      runValidation(errors, check.code(), () -> check.action().accept(workflows, globalAgents));
    }
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
