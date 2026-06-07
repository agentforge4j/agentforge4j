package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * A {@link ToolPolicy} decision for a requested tool invocation.
 */
public sealed interface PolicyDecision
    permits PolicyDecision.Allow, PolicyDecision.RequireApproval, PolicyDecision.Deny {

  /**
   * Allow the invocation to proceed.
   */
  record Allow() implements PolicyDecision {

  }

  /**
   * Suspend the invocation for human approval.
   *
   * @param reason        non-blank reason shown to the approver
   * @param approverScope required approver scope; persisted as data, not enforced in OSS; may be
   *                      {@code null}
   */
  record RequireApproval(String reason, String approverScope) implements PolicyDecision {

    /**
     * Validates that {@code reason} is non-blank.
     */
    public RequireApproval {
      Validate.notBlank(reason, "RequireApproval reason must not be blank");
    }
  }

  /**
   * Reject the invocation outright.
   *
   * @param reason non-blank deny reason
   */
  record Deny(String reason) implements PolicyDecision {

    /**
     * Validates that {@code reason} is non-blank.
     */
    public Deny {
      Validate.notBlank(reason, "Deny reason must not be blank");
    }
  }
}
