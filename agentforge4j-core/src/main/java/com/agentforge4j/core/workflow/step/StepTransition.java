package com.agentforge4j.core.workflow.step;

/**
 * Human gate applied after a step completes before the next step may run.
 */
public enum StepTransition {
  /**
   * Proceeds to the next step without human interaction when policy allows.
   */
  AUTO,
  /**
   * Requires a human review outcome but not necessarily a formal approval record.
   */
  HUMAN_REVIEW,
  /**
   * Requires an explicit approval decision matching
   * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_APPROVAL}.
   */
  HUMAN_APPROVAL
}
