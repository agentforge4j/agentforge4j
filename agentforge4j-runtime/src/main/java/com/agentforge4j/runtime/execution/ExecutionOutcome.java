// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

/**
 * The outcome of executing a single {@link com.agentforge4j.core.workflow.Executable}.
 *
 * <p>The execution engine uses this to decide whether to advance to the next
 * sibling step, pause the run, or stop looping.
 */
public enum ExecutionOutcome {

  /**
   * The executable completed and the engine should advance to the next sibling.
   */
  COMPLETED,

  /**
   * Execution produced a pause — the runtime has transitioned the state to {@code AWAITING_INPUT},
   * {@code AWAITING_APPROVAL}, or {@code PAUSED}. The caller should stop driving the loop until
   * resumed via the command model.
   */
  PAUSED,

  /**
   * The executable failed — the runtime has transitioned the state to {@code FAILED}.
   */
  FAILED
}
