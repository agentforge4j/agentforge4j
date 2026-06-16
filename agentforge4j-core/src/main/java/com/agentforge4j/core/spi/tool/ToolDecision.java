// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * An operator's decision for a tool invocation suspended in
 * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_DECISION} after policy denied it or it
 * failed (after retries). Distinct from {@link ApprovalDecision}, which gates a tool
 * <em>before</em> execution.
 *
 * <p>Each variant carries the {@code actorId} of the operator who made the decision, mirroring
 * {@link ApprovalDecision} carrying the actor inside the decision record.
 */
public sealed interface ToolDecision permits ToolDecision.Continue, ToolDecision.Retry {

  /**
   * Opaque identifier supplied by the embedding application representing the entity responsible for the action.
   * AgentForge4j treats the value as opaque and does not interpret its structure or meaning.
   *
   * @return the opaque actor id; never blank
   */
  String actorId();

  /**
   * Proceed without the tool result; the runtime writes {@code tool.<capability>.error} to context and advances the
   * run.
   *
   * @param actorId opaque id of the operator who made the decision; never blank
   */
  record Continue(String actorId) implements ToolDecision {

    public Continue {
      Validate.notBlank(actorId, "ToolDecision.Continue actorId must not be blank");
    }
  }

  /**
   * Replay the exact stored call (re-resolve, re-validate, invoke) without re-invoking the LLM.
   *
   * @param actorId opaque id of the operator who made the decision; never blank
   */
  record Retry(String actorId) implements ToolDecision {

    public Retry {
      Validate.notBlank(actorId, "ToolDecision.Retry actorId must not be blank");
    }
  }
}
