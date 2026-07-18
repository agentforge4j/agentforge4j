// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

/**
 * Thrown by {@link ToolExecutionService#resume} when the pending invocation observed at the start
 * of the call no longer matches the row an atomic {@link PendingToolInvocationStore#claim} attempt
 * finds — a concurrent {@code resume} call already claimed it, or replaced it with a different
 * pending row, between this call's peek and its own claim attempt.
 *
 * <p>This is a benign concurrency-loss signal, never a provider/tool failure: the provider for
 * this invocation is never invoked by the losing call, and the caller must not mutate run state,
 * apply an error result, or re-suspend the run in response to it — the invocation's actual
 * resolution belongs to whichever call won the race, or, if the row was replaced, to whatever
 * later resolves the replacement.
 */
public final class ToolInvocationClaimLostException extends RuntimeException {

  /**
   * @param message human-readable detail identifying the invocation that could not be claimed
   */
  public ToolInvocationClaimLostException(String message) {
    super(message);
  }
}
