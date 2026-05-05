package com.agentforge4j.core.workflow.step.loop;

import com.agentforge4j.util.Validate;

/**
 * Loop driver settings for blueprint expansion: how iterations end, optional evaluator or for-each
 * key, and iteration cap.
 *
 * @param terminationStrategy non-null strategy; additional fields are required or ignored depending
 *                            on this value
 * @param forEachContextKey   required when {@code terminationStrategy} is
 *                            {@link LoopTerminationStrategy#FOR_EACH}; otherwise ignored
 * @param evaluatorAgentId    required when {@code terminationStrategy} is
 *                            {@link LoopTerminationStrategy#EVALUATOR}; otherwise ignored
 * @param maxIterations       at least one; caps iterations regardless of strategy
 * @param maxIterationsAction if {@code null} at construction, defaults to
 *                            {@link MaxIterationsAction#AWAIT_USER}
 */
public record LoopConfig(
    LoopTerminationStrategy terminationStrategy,
    String forEachContextKey,
    String evaluatorAgentId,
    int maxIterations,
    MaxIterationsAction maxIterationsAction
) {

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
    Validate.isGreaterThanZero(maxIterations,"LoopConfig maxIterations must be at least 1");
    maxIterationsAction =
        maxIterationsAction != null ? maxIterationsAction : MaxIterationsAction.AWAIT_USER;
  }
}
