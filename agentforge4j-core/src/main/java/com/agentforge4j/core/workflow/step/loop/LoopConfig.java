// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.loop;

import com.agentforge4j.util.Validate;

/**
 * Loop driver settings for blueprint expansion: how iterations end, optional evaluator or for-each
 * key, and iteration cap.
 *
 * @param terminationStrategy      non-null strategy; additional fields are required or ignored
 *                                 depending on this value
 * @param forEachContextKey        required when {@code terminationStrategy} is
 *                                 {@link LoopTerminationStrategy#FOR_EACH}; otherwise ignored
 * @param evaluatorAgentId         required when {@code terminationStrategy} is
 *                                 {@link LoopTerminationStrategy#EVALUATOR}; otherwise ignored
 * @param maxIterations            at least one; caps iterations regardless of strategy
 * @param maxIterationsAction      if {@code null} at construction, defaults to
 *                                 {@link MaxIterationsAction#AWAIT_USER}
 * @param allowForEachListMutation only meaningful for {@link LoopTerminationStrategy#FOR_EACH}.
 *                                 When {@code false} (default), the runtime fails the run if the
 *                                 list under {@code forEachContextKey} changes between pause and
 *                                 resume. When {@code true}, the runtime accepts the new list, logs
 *                                 the change, and restarts iteration from element 1 against the new
 *                                 list (any previously-completed iterations' side effects remain in
 *                                 step outputs / context and are not re-run for the same blueprint
 *                                 instance — but new list elements at the same index re-trigger the
 *                                 iteration body).
 */
public record LoopConfig(
    LoopTerminationStrategy terminationStrategy,
    String forEachContextKey,
    String evaluatorAgentId,
    int maxIterations,
    MaxIterationsAction maxIterationsAction,
    boolean allowForEachListMutation
) {

  /**
   * Creates a {@link LoopConfig} with {@code allowForEachListMutation} set to {@code false}.
   */
  public static LoopConfig withDefaults(
      LoopTerminationStrategy terminationStrategy,
      String forEachContextKey,
      String evaluatorAgentId,
      int maxIterations,
      MaxIterationsAction maxIterationsAction) {
    return new LoopConfig(
        terminationStrategy,
        forEachContextKey,
        evaluatorAgentId,
        maxIterations,
        maxIterationsAction,
        false);
  }

  public LoopConfig {
    Validate.notNull(terminationStrategy, "LoopConfig terminationStrategy must not be null");
    if (terminationStrategy == LoopTerminationStrategy.FOR_EACH) {
      Validate.notBlank(forEachContextKey,
          "LoopConfig forEachContextKey is required for FOR_EACH strategy");
    }
    if (terminationStrategy == LoopTerminationStrategy.EVALUATOR) {
      Validate.notBlank(evaluatorAgentId,
          "LoopConfig evaluatorAgentId is required for EVALUATOR strategy");
    }
    Validate.isGreaterThanZero(maxIterations, "LoopConfig maxIterations must be at least 1");
    maxIterationsAction =
        maxIterationsAction != null ? maxIterationsAction : MaxIterationsAction.AWAIT_USER;
  }
}
