// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.blueprint;

import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.TransitionAware;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.util.Validate;

/**
 * Loop configuration and post-loop transition for a reusable blueprint.
 *
 * @param loopConfig optional loop driver; may be {@code null} when the blueprint is linear
 * @param transition non-null gate after the blueprint body completes
 */
public record BlueprintBehaviour(
    LoopConfig loopConfig,
    StepTransition transition
) implements TransitionAware {

  public BlueprintBehaviour {
    Validate.notNull(transition, "BlueprintBehaviour transition must not be null");
  }
}
