// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.util.Validate;
import java.time.Instant;

/**
 * A tool invocation suspended awaiting human approval, persisted by
 * {@link PendingToolInvocationStore}.
 *
 * @param toolInvocationId non-blank invocation id (audit and approval-resume correlation)
 * @param runId            non-blank owning run id
 * @param stepUid          step instance id, or {@code null}
 * @param agentId          requesting agent, or {@code null}
 * @param workflowId       owning workflow id, or {@code null}
 * @param capability       non-blank capability id
 * @param arguments        tool arguments as JSON text, or {@code null}
 * @param llmRationale     model rationale for audit, or {@code null}
 * @param reason           approval reason from the policy decision, or {@code null}
 * @param approverScope    required approver scope (persisted as data, not enforced in OSS), or
 *                         {@code null}
 * @param origin           non-null discriminator for why the invocation is pending; governs which
 *                         {@link ApprovalDecision} / {@code ToolDecision} may resolve it (see
 *                         {@link Origin})
 * @param createdAt        non-null creation time
 */
public record PendingToolInvocation(
    String toolInvocationId,
    String runId,
    String stepUid,
    String agentId,
    String workflowId,
    String capability,
    String arguments,
    String llmRationale,
    String reason,
    String approverScope,
    Origin origin,
    Instant createdAt) {

  /**
   * Validates the identity, capability, origin, and timestamp.
   */
  public PendingToolInvocation {
    Validate.notBlank(toolInvocationId, "PendingToolInvocation toolInvocationId must not be blank");
    Validate.notBlank(runId, "PendingToolInvocation runId must not be blank");
    Validate.notBlank(capability, "PendingToolInvocation capability must not be blank");
    Validate.notNull(origin, "PendingToolInvocation origin must not be null");
    Validate.notNull(createdAt, "PendingToolInvocation createdAt must not be null");
  }

  /**
   * Discriminates why an invocation is suspended pending an operator decision, so a resume attempt
   * can be checked against the reason it suspended in the first place.
   *
   * <p>A {@link #POLICY_DENIED} row is terminal for that invocation: only a non-executing decision
   * ({@link ApprovalDecision.Reject}, or the runtime's {@code ToolDecision.Continue}, which never
   * calls {@link ToolExecutionService#resume}) may resolve it. {@link ApprovalDecision.Approve} —
   * whether via the runtime's {@code ToolDecision.Retry} or a direct SPI call — must never execute a
   * denied invocation; {@link ToolExecutionService#resume} rejects that transition without invoking
   * the provider.
   */
  public enum Origin {
    /**
     * The invocation was refused by {@link ToolPolicy} ({@link PolicyDecision.Deny}). Terminal: not
     * retryable/approvable.
     */
    POLICY_DENIED,
    /**
     * The invocation failed during resolve, validate, or invoke (after the service's own retries).
     * Retryable: a fresh attempt genuinely re-runs the call.
     */
    EXECUTION_FAILED,
    /**
     * The invocation requires human approval ({@link PolicyDecision.RequireApproval}) before its
     * first attempt. Approvable: an {@link ApprovalDecision.Approve} performs the original,
     * not-yet-attempted call.
     */
    APPROVAL_REQUIRED
  }

  /**
   * Assembles a pending invocation awaiting policy approval, from the requesting command, its
   * invocation context, and the {@link PolicyDecision.RequireApproval} that suspended it, so
   * callers do not thread the eleven components positionally.
   *
   * @param command       non-null requesting command (supplies id, capability, rationale)
   * @param context       non-null invocation context (supplies run, step, agent, workflow scope)
   * @param approval      non-null approval requirement (supplies reason and approver scope)
   * @param argumentsJson tool arguments serialized to JSON text, or {@code null}
   * @param createdAt     non-null creation time
   *
   * @return the assembled pending invocation
   */
  public static PendingToolInvocation pending(
      final ToolInvocationCommand command,
      final ToolInvocationContext context,
      final PolicyDecision.RequireApproval approval,
      final String argumentsJson,
      final Instant createdAt) {
    Validate.notNull(command, "PendingToolInvocation command must not be null");
    Validate.notNull(context, "PendingToolInvocation context must not be null");
    Validate.notNull(approval, "PendingToolInvocation approval must not be null");
    return new PendingToolInvocation(
        command.toolInvocationId(),
        context.runId(),
        context.stepUid(),
        context.agentId(),
        context.scope().workflowId(),
        command.capability(),
        argumentsJson,
        command.llmRationale(),
        approval.reason(),
        approval.approverScope(),
        Origin.APPROVAL_REQUIRED,
        createdAt);
  }

  /**
   * Assembles a pending invocation awaiting an operator {@code ToolDecision} after a tool was
   * denied by policy or failed (after retries). It carries no approver scope — that is a
   * policy-approval concept — and records the denial/failure {@code reason} for the operator.
   *
   * @param command       non-null requesting command (supplies id, capability, rationale)
   * @param context       non-null invocation context (supplies run, step, agent, workflow scope)
   * @param argumentsJson tool arguments serialized to JSON text, or {@code null}
   * @param reason        human-readable denial or failure detail, or {@code null}
   * @param origin        non-null origin; must be {@link Origin#POLICY_DENIED} or
   *                      {@link Origin#EXECUTION_FAILED}
   * @param createdAt     non-null creation time
   *
   * @return the assembled pending invocation
   */
  public static PendingToolInvocation forDecision(
      final ToolInvocationCommand command,
      final ToolInvocationContext context,
      final String argumentsJson,
      final String reason,
      final Origin origin,
      final Instant createdAt) {
    Validate.notNull(command, "PendingToolInvocation command must not be null");
    Validate.notNull(context, "PendingToolInvocation context must not be null");
    Validate.notNull(origin, "PendingToolInvocation origin must not be null");
    Validate.isTrue(origin == Origin.POLICY_DENIED || origin == Origin.EXECUTION_FAILED,
        "PendingToolInvocation.forDecision origin must be POLICY_DENIED or EXECUTION_FAILED, was %s"
            .formatted(origin));
    return new PendingToolInvocation(
        command.toolInvocationId(),
        context.runId(),
        context.stepUid(),
        context.agentId(),
        context.scope().workflowId(),
        command.capability(),
        argumentsJson,
        command.llmRationale(),
        reason,
        null,
        origin,
        createdAt);
  }
}
