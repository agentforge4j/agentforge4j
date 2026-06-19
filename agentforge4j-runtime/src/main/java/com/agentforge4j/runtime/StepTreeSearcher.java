// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Locates {@link Executable} nodes (typically {@link StepDefinition}) within a workflow tree.
 */
final class StepTreeSearcher {

  Executable findStep(WorkflowDefinition workflow, String stepId) {
    return Validate.notNull(findInSteps(workflow.steps(), workflow, stepId),
        "Step '%s' not found in workflow '%s'".formatted(stepId, workflow.id()));
  }

  /**
   * Returns the top-level {@link StepDefinition} with the given id, or {@code null} when no direct entry of
   * {@code workflow.steps()} is a step with that id. Does not descend into blueprints or sub-workflows.
   */
  StepDefinition findTopLevelStep(WorkflowDefinition workflow, String stepId) {
    for (Executable executable : workflow.steps()) {
      if (executable instanceof StepDefinition step && step.stepId().equals(stepId)) {
        return step;
      }
    }
    return null;
  }

  /**
   * Returns the id of the top-level entry whose subtree contains {@code stepId} when the step exists only nested inside
   * a blueprint or sub-workflow (the blueprint id or sub-workflow id, respectively), or {@code null} when the step is
   * not found nested under any top-level entry. Used to point a rejected nested-retry request at its enclosing
   * top-level step.
   */
  String findEnclosingTopLevelId(WorkflowDefinition workflow, String stepId) {
    for (Executable executable : workflow.steps()) {
      if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        if (blueprint != null && findInSteps(blueprint.steps(), workflow, stepId) != null) {
          return ref.blueprintId();
        }
      } else if (executable instanceof WorkflowDefinition nested
          && findInSteps(nested.steps(), nested, stepId) != null) {
        return nested.id();
      }
    }
    return null;
  }

  Executable findInSteps(List<Executable> steps, WorkflowDefinition enclosing, String stepId) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step && step.stepId().equals(stepId)) {
        return step;
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition bp = enclosing.blueprints().get(ref.blueprintId());
        Executable found = bp == null ? null : findInSteps(bp.steps(), enclosing, stepId);
        if (found != null) {
          return found;
        }
      } else if (executable instanceof WorkflowDefinition nested) {
        Executable found = findInSteps(nested.steps(), nested, stepId);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
