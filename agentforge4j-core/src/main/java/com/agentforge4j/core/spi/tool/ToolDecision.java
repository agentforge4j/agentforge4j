package com.agentforge4j.core.spi.tool;

/**
 * An operator's decision for a tool invocation suspended in
 * {@link com.agentforge4j.core.workflow.state.WorkflowStatus#AWAITING_TOOL_DECISION} after policy
 * denied it or it failed (after retries). Distinct from {@link ApprovalDecision}, which gates a
 * tool
 * <em>before</em> execution.
 */
public sealed interface ToolDecision permits ToolDecision.Continue, ToolDecision.Retry {

  /**
   * Proceed without the tool result; the runtime writes {@code tool.<capability>.error} to context
   * and advances the run.
   */
  record Continue() implements ToolDecision {

  }

  /**
   * Replay the exact stored call (re-resolve, re-validate, invoke) without re-invoking the LLM.
   */
  record Retry() implements ToolDecision {

  }
}
