// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

/**
 * Thrown by {@link ToolExecutionService#resume} when an {@link ApprovalDecision.Approve} targets a
 * {@link PendingToolInvocation} whose {@link PendingToolInvocation.Origin} is
 * {@link PendingToolInvocation.Origin#POLICY_DENIED}.
 *
 * <p>A {@link ToolPolicy} {@link PolicyDecision.Deny} is terminal for that invocation: neither the
 * runtime's {@code ToolDecision.Retry} nor a direct SPI {@code resume(..., Approve)} may resurrect
 * it into execution, no matter how it is invoked — this is the single chokepoint's enforcement of
 * that rule. Only {@link ApprovalDecision.Reject}, or the runtime's non-executing
 * {@code ToolDecision.Continue} (which never calls {@code resume}), may resolve a denied
 * invocation. The pending row is left untouched by the rejected attempt, so a later legitimate
 * Reject/Continue can still resolve it.
 */
public final class PolicyDenialTerminalException extends RuntimeException {

  /**
   * Creates the exception with a detail message.
   *
   * @param message the detail message, naming the invocation and run
   */
  public PolicyDenialTerminalException(String message) {
    super(message);
  }
}
