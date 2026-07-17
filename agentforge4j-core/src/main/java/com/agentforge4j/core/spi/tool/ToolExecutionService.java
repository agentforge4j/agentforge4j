// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;

/**
 * The single chokepoint for tool invocation: resolve, validate, evaluate policy, then invoke under
 * an authoritative timeout. No tool invocation may bypass this service.
 */
public interface ToolExecutionService {

  /**
   * Executes a requested tool invocation.
   *
   * @param cmd the requested invocation
   * @param ctx invocation context
   *
   * @return the terminal outcome
   */
  ToolExecutionOutcome execute(ToolInvocationCommand cmd, ToolInvocationContext ctx);

  /**
   * Resumes a previously suspended tool invocation — awaiting approval
   * ({@link ToolExecutionOutcome.Status#APPROVAL_PENDING}), or awaiting an operator decision after a
   * policy denial or an execution failure — after a human decision.
   *
   * <p>For an {@link PendingToolInvocation.Origin#APPROVAL_REQUIRED} or
   * {@link PendingToolInvocation.Origin#EXECUTION_FAILED} row, the human decision supersedes the
   * original {@link PolicyDecision.RequireApproval} rule: policy is not re-evaluated. The capability
   * is re-resolved and arguments re-validated against current configuration, and the pending record
   * is atomically claimed (see {@link PendingToolInvocationStore#claim}) before invocation so that
   * two concurrent resumes on the same invocation never both execute; the loser observes a
   * claimed/not-pending {@link ToolExecutionOutcome.Status#FAILED} outcome instead. If the claimed
   * attempt itself fails, a fresh pending row is persisted so the operator gets a further decision
   * point.
   *
   * <p>For a {@link PendingToolInvocation.Origin#POLICY_DENIED} row the denial is terminal:
   * {@link ApprovalDecision.Approve} is rejected with a {@link PolicyDenialTerminalException}
   * without invoking the provider and without consuming the pending row; only
   * {@link ApprovalDecision.Reject} resolves it through this method (the runtime's non-executing
   * {@code ToolDecision.Continue} resolves it without ever calling {@code resume}).
   *
   * @param runId            non-blank run that owns the pending invocation
   * @param toolInvocationId non-blank pending invocation id
   * @param decision         the human decision
   *
   * @return the terminal outcome
   *
   * @throws PolicyDenialTerminalException if {@code decision} is {@link ApprovalDecision.Approve}
   *                                        and the pending row's origin is
   *                                        {@link PendingToolInvocation.Origin#POLICY_DENIED}
   */
  ToolExecutionOutcome resume(String runId, String toolInvocationId, ApprovalDecision decision);
}
