// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;

/**
 * Drives loop iterations for a blueprint.
 *
 * <p>Implementations are keyed by {@link LoopTerminationStrategy} and selected
 * by {@code BlueprintExecutor} when the blueprint has a loop configuration.
 */
public interface LoopStrategy {

  /**
   * @return the strategy this implementation handles
   */
  LoopTerminationStrategy strategy();

  /**
   * Iterate over the blueprint body until termination or {@code maxIterations}.
   *
   * @return the outcome of the loop — {@code COMPLETED} when termination fired normally,
   * {@code PAUSED} if an iteration paused the run, or {@code FAILED} if an iteration failed or
   * {@code MaxIterationsAction.FAIL} triggered.
   */
  ExecutionOutcome iterate(BlueprintDefinition blueprint,
      LoopConfig config,
      ExecutionContext executionContext);
}
