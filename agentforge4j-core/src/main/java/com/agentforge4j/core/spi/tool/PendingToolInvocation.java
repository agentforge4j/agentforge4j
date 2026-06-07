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
    Instant createdAt) {

  /**
   * Validates the identity, capability, and timestamp.
   */
  public PendingToolInvocation {
    Validate.notBlank(toolInvocationId, "PendingToolInvocation toolInvocationId must not be blank");
    Validate.notBlank(runId, "PendingToolInvocation runId must not be blank");
    Validate.notBlank(capability, "PendingToolInvocation capability must not be blank");
    Validate.notNull(createdAt, "PendingToolInvocation createdAt must not be null");
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
   * @param createdAt     non-null creation time
   *
   * @return the assembled pending invocation
   */
  public static PendingToolInvocation forDecision(
      final ToolInvocationCommand command,
      final ToolInvocationContext context,
      final String argumentsJson,
      final String reason,
      final Instant createdAt) {
    Validate.notNull(command, "PendingToolInvocation command must not be null");
    Validate.notNull(context, "PendingToolInvocation context must not be null");
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
        createdAt);
  }
}
