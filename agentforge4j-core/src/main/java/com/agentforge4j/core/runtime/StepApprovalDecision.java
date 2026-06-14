package com.agentforge4j.core.runtime;

import com.agentforge4j.util.Validate;

/**
 * A human decision for a step suspended in
 * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_STEP_APPROVAL} by a
 * {@link com.agentforge4j.core.workflow.step.StepTransition#HUMAN_APPROVAL} gate. Mirrors
 * {@link com.agentforge4j.core.spi.tool.ApprovalDecision}: the actor is carried inside the decision.
 *
 * <p>{@link Approve} advances the run; {@link Reject} fails it (no send-back).
 */
public sealed interface StepApprovalDecision permits StepApprovalDecision.Approve, StepApprovalDecision.Reject {

  /**
   * Approve the step and advance the run.
   *
   * @param approvedBy opaque id of the approver; never blank
   * @param note       optional human-readable note recorded on the event; may be blank
   */
  record Approve(String approvedBy, String note) implements StepApprovalDecision {
    public Approve {
      Validate.notBlank(approvedBy, "StepApprovalDecision.Approve approvedBy must not be blank");
    }
  }

  /**
   * Reject the step and fail the run.
   *
   * @param rejectedBy opaque id of the rejecter; never blank
   * @param reason     optional human-readable reason recorded on the event and the failure; may be blank
   */
  record Reject(String rejectedBy, String reason) implements StepApprovalDecision {
    public Reject {
      Validate.notBlank(rejectedBy, "StepApprovalDecision.Reject rejectedBy must not be blank");
    }
  }
}
