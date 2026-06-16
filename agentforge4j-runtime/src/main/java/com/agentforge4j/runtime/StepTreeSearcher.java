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
