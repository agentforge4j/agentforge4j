package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * A human decision on a pending tool invocation, supplied to {@link ToolExecutionService#resume}.
 */
public sealed interface ApprovalDecision
    permits ApprovalDecision.Approve, ApprovalDecision.Reject {

  /**
   * Approve the pending invocation.
   *
   * @param approvedBy non-blank id of the approver
   */
  record Approve(String approvedBy) implements ApprovalDecision {

    /**
     * Validates that {@code approvedBy} is non-blank.
     */
    public Approve {
      Validate.notBlank(approvedBy, "Approve approvedBy must not be blank");
    }
  }

  /**
   * Reject the pending invocation.
   *
   * @param rejectedBy non-blank id of the rejecter
   * @param reason     reason for rejection, or {@code null}
   */
  record Reject(String rejectedBy, String reason) implements ApprovalDecision {

    /**
     * Validates that {@code rejectedBy} is non-blank.
     */
    public Reject {
      Validate.notBlank(rejectedBy, "Reject rejectedBy must not be blank");
    }
  }
}
