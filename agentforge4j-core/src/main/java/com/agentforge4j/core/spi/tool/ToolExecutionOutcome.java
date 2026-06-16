// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * Terminal outcome of {@link ToolExecutionService#execute} or {@link ToolExecutionService#resume}.
 *
 * @param status non-null status discriminator
 * @param result tool result; {@code null} unless {@link Status#EXECUTED} or {@link Status#FAILED}
 * @param detail human-readable detail, or {@code null}
 */
public record ToolExecutionOutcome(Status status, ToolResult result, String detail) {

  /**
   * Validates that {@code status} is non-null.
   */
  public ToolExecutionOutcome {
    Validate.notNull(status, "ToolExecutionOutcome status must not be null");
  }

  /**
   * Creates an {@link Status#EXECUTED} outcome wrapping a successful result.
   *
   * @param result the successful tool result
   *
   * @return the executed outcome
   */
  public static ToolExecutionOutcome executed(ToolResult result) {
    return new ToolExecutionOutcome(Status.EXECUTED, result, null);
  }

  /**
   * Creates a {@link Status#FAILED} outcome.
   *
   * @param result the failed tool result, or {@code null}
   * @param detail human-readable failure detail
   *
   * @return the failed outcome
   */
  public static ToolExecutionOutcome failed(ToolResult result, String detail) {
    return new ToolExecutionOutcome(Status.FAILED, result, detail);
  }

  /**
   * Creates a {@link Status#DENIED} outcome.
   *
   * @param detail human-readable deny reason
   *
   * @return the denied outcome
   */
  public static ToolExecutionOutcome denied(String detail) {
    return new ToolExecutionOutcome(Status.DENIED, null, detail);
  }

  /**
   * Creates an {@link Status#APPROVAL_PENDING} outcome.
   *
   * @param detail human-readable approval reason
   *
   * @return the approval-pending outcome
   */
  public static ToolExecutionOutcome approvalPending(String detail) {
    return new ToolExecutionOutcome(Status.APPROVAL_PENDING, null, detail);
  }

  /**
   * Lifecycle status of a tool invocation.
   */
  public enum Status {
    EXECUTED,
    DENIED,
    APPROVAL_PENDING,
    FAILED
  }
}
