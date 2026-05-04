package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.util.Validate;

/**
 * Step that collects user input for the artifact identified by {@code artifactId} before
 * continuing.
 *
 * @param artifactId non-blank id of a defined artifact to fill
 * @param transition non-null gate after input is satisfied
 */
public record InputBehaviour(
    String artifactId,
    StepTransition transition
) implements StepBehaviour {

  public InputBehaviour {
    Validate.notBlank(artifactId, "InputBehaviour artifactId must not be blank");
    Validate.notNull(transition, "InputBehaviour transition must not be null");
  }
}
