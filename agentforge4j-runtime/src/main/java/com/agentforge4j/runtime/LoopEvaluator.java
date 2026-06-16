// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.runtime.execution.ExecutionContext;

/**
 * Evaluates whether an
 * {@link com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy#EVALUATOR} loop should
 * terminate after the current iteration.
 *
 * <p>Kept as an interface so that the evaluator can be backed by an agent call
 * without the loop strategy having to know the full LLM pipeline.
 */
public interface LoopEvaluator {

  /**
   * @param evaluatorAgentId id of the agent registered in the repository
   * @param iteration        current iteration number, 1-based
   * @param executionContext the run-time execution context
   * @return true if the loop should terminate, false to continue iterating
   */
  boolean shouldTerminate(String evaluatorAgentId, int iteration,
      ExecutionContext executionContext);
}
