// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;

/**
 * A single reachable step occurrence in a workflow graph: the step, its id, and the structural location at which it is
 * reachable.
 *
 * <p>The {@code location} is a defining-container path (for example
 * {@code wf:root/bp:loop/step:review}) that distinguishes the same step id reached at two different structural
 * locations from a single definition reached by more than one path to the same container.
 *
 * @param stepId   the step id
 * @param location the structural location key at which the step is reachable
 * @param step     the reachable step definition
 */
public record ReachableStep(String stepId, String location, StepDefinition step) {

  /**
   * Validates the occurrence.
   */
  public ReachableStep {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(location, "location must not be blank");
    Validate.notNull(step, "step must not be null");
  }
}
