package com.agentforge4j.core.workflow.state;

/**
 * High-level lifecycle state of a workflow run persisted in {@link WorkflowState}.
 */
public enum WorkflowStatus {
  /**
   * Steps may execute; run is not waiting on a human gate.
   */
  RUNNING,
  /**
   * Run is suspended and may be resumed later.
   */
  PAUSED,
  /**
   * Run waits for user-supplied data (for example artifact or prompt completion).
   */
  AWAITING_INPUT,
  /**
   * Run waits for an explicit approval decision before continuing.
   */
  AWAITING_APPROVAL,
  /**
   * All steps finished successfully.
   */
  COMPLETED,
  /**
   * A step or runtime policy ended the run with failure.
   */
  FAILED,
  /**
   * Run was aborted without successful completion.
   */
  CANCELLED
}
