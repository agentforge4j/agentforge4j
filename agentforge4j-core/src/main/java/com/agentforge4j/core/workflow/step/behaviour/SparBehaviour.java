// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.core.workflow.step.spar.SparConfig;
import com.agentforge4j.util.Validate;

/**
 * Step that runs an adversarial exchange between the primary agent and a challenger before
 * continuing.
 *
 * @param agentRef    non-blank reference to the primary agent definition
 * @param sparConfig  non-null spar parameters
 * @param transition  non-null gate after the spar completes
 * @param retryPolicy if {@code null} at construction, replaced with {@link RetryPolicy#none()}
 */
public record SparBehaviour(
    String agentRef,
    SparConfig sparConfig,
    StepTransition transition,
    RetryPolicy retryPolicy
) implements StepBehaviour, TransitionAware {

  public SparBehaviour {
    Validate.notBlank(agentRef, "SparBehaviour agentRef must not be blank");
    Validate.notNull(sparConfig, "SparBehaviour sparConfig must not be null");
    Validate.notNull(transition, "SparBehaviour transition must not be null");
    retryPolicy = retryPolicy != null ? retryPolicy : RetryPolicy.none();
  }
}
