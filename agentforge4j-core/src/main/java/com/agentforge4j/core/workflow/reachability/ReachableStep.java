// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;

/**
 * A single reachable step occurrence in a workflow graph: the step, its id, the structural location at which it is
 * reachable, and the workflow that declares it.
 *
 * <p>The {@code location} is a defining-container path (for example
 * {@code wf:root/bp:loop/step:review}) that distinguishes the same step id reached at two different structural
 * locations from a single definition reached by more than one path to the same container.
 *
 * <p>{@code declaringWorkflow} is the innermost enclosing {@link WorkflowDefinition} at this location — the same
 * workflow whose {@code requirements()} a {@code STEP_ACTION} requirement targeting this step must be declared on. A
 * blueprint-ref descent does not change it (a blueprint is not itself a workflow); an inline nested
 * {@code WorkflowDefinition} or a {@code WORKFLOW}-behaviour step's resolved sub-workflow does.
 *
 * @param stepId            the step id
 * @param location          the structural location key at which the step is reachable
 * @param step              the reachable step definition
 * @param declaringWorkflow the innermost enclosing workflow at this location
 */
public record ReachableStep(String stepId, String location, StepDefinition step,
    WorkflowDefinition declaringWorkflow) {

  /**
   * Validates the occurrence.
   */
  public ReachableStep {
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(location, "location must not be blank");
    Validate.notNull(step, "step must not be null");
    Validate.notNull(declaringWorkflow, "declaringWorkflow must not be null");
  }
}
