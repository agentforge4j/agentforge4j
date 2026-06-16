// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.loop;

/**
 * How a blueprint loop decides each iteration has finished and whether another iteration should
 * run.
 */
public enum LoopTerminationStrategy {
  /**
   * Stops when the active agent signals completion for the iteration.
   */
  AGENT_SIGNAL,
  /**
   * Stops when the configured evaluator agent decides the loop is done; requires
   * {@link LoopConfig#evaluatorAgentId()}.
   */
  EVALUATOR,
  /**
   * Stops after a fixed number of iterations regardless of agent output.
   */
  FIXED_COUNT,
  /**
   * Runs once per element in the collection bound to {@link LoopConfig#forEachContextKey()}.
   */
  FOR_EACH
}
