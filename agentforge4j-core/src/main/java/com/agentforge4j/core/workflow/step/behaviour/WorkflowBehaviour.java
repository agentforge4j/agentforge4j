// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.util.Validate;

/**
 * Step that runs a nested workflow by reference.
 *
 * @param workflowRef non-blank reference to another workflow definition
 * @param transition  non-null gate after the nested workflow completes
 */
public record WorkflowBehaviour(
    String workflowRef,
    StepTransition transition
) implements StepBehaviour, TransitionAware {

  public WorkflowBehaviour {
    Validate.notBlank(workflowRef, "WorkflowBehaviour workflowRef must not be blank");
    Validate.notNull(transition,
        "WorkflowBehaviour transition must not be null for workflowRef: %s".formatted(workflowRef));
  }
}
