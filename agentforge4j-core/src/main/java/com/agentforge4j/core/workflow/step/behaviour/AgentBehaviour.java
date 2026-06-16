// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.util.Validate;

/**
 * Step that invokes a single agent with optional retry policy.
 *
 * @param agentRef    non-blank reference to the agent definition
 * @param transition  non-null gate after the agent step completes
 * @param retryPolicy if {@code null} at construction, replaced with {@link RetryPolicy#none()}
 */
public record AgentBehaviour(
    String agentRef,
    StepTransition transition,
    RetryPolicy retryPolicy
) implements StepBehaviour, TransitionAware {

  public AgentBehaviour {
    Validate.notBlank(agentRef, "AgentBehaviour agentRef must not be blank");
    Validate.notNull(transition, "AgentBehaviour transition must not be null");
    retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.none();
  }
}
