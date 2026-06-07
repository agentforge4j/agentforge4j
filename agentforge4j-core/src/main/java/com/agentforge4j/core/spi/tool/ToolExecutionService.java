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
   * Resumes a previously suspended ({@link ToolExecutionOutcome.Status#APPROVAL_PENDING})
   * invocation after a human decision.
   *
   * <p>The human decision supersedes the {@link PolicyDecision.RequireApproval} rule: policy is
   * not
   * re-evaluated. The capability is re-resolved and arguments re-validated against current
   * configuration, and the pending record is removed on every terminal outcome.
   *
   * @param runId            non-blank run that owns the pending invocation
   * @param toolInvocationId non-blank pending invocation id
   * @param decision         the human decision
   *
   * @return the terminal outcome
   */
  ToolExecutionOutcome resume(String runId, String toolInvocationId, ApprovalDecision decision);
}
