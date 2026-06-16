// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.util.Validate;

/**
 * Step that loads content from {@code resourcePath} into workflow context under
 * {@code contextKey}.
 *
 * @param resourcePath non-blank classpath, file path, or URI understood by the runtime
 * @param contextKey   non-blank key written into
 *                     {@link com.agentforge4j.core.workflow.state.WorkflowState} context
 * @param transition   non-null gate after the resource is available
 */
public record ResourceBehaviour(
    String resourcePath,
    String contextKey,
    StepTransition transition
) implements StepBehaviour, TransitionAware {

  public ResourceBehaviour {
    Validate.notBlank(resourcePath, "ResourceBehaviour resourcePath must not be blank");
    Validate.notBlank(contextKey, "ResourceBehaviour contextKey must not be blank");
    Validate.notNull(transition, "ResourceBehaviour transition must not be null");
  }
}
