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
   * Run waits for an explicit approval decision before continuing (an {@code ESCALATE} command).
   * Resumed via {@code WorkflowRuntime.approve}.
   */
  AWAITING_APPROVAL,
  /**
   * Run waits for a human to approve a tool invocation <em>before</em> it executes (policy
   * {@code RequireApproval}). Distinct from {@link #AWAITING_APPROVAL} (escalation): resumed via
   * {@code WorkflowRuntime.continueAfterToolApproval}, never {@code approve}.
   */
  AWAITING_TOOL_APPROVAL,
  /**
   * Run waits for an operator {@code ToolDecision} (continue or retry) <em>after</em> a tool
   * invocation was denied by policy or failed after retries. Resumed via
   * {@code WorkflowRuntime.resolveToolDecision}, never {@code approve}.
   */
  AWAITING_TOOL_DECISION,
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
