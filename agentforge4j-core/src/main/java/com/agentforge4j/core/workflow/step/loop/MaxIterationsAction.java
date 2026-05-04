package com.agentforge4j.core.workflow.step.loop;

/**
 * Action taken when a loop reaches {@link LoopConfig#maxIterations()} without terminating by
 * strategy.
 */
public enum MaxIterationsAction {
  /**
   * Pauses the run until external input or operator intervention continues it.
   */
  AWAIT_USER,
  /**
   * Marks the run as failed according to runtime policy.
   */
  FAIL
}
