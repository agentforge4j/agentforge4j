// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

/**
 * Outcome of applying a sequence of {@link com.agentforge4j.core.command.LlmCommand}s in order.
 *
 * <p>Behaviour handlers translate this to execution outcomes via non-exported
 * {@code CommandApplicationResults} helpers so command handling stays decoupled from the execution
 * package.
 */
public enum CommandApplicationResult {

  /**
   * Batch applied so far with no pause, completion signal, or escalation — execution continues with
   * the next command or step.
   */
  CONTINUE,

  /**
   * A {@code COMPLETE} command was applied — the enclosing loop should treat the iteration as
   * finished.
   */
  COMPLETE_SIGNAL,

  /**
   * A {@code USER_PROMPT} with {@code responseRequired=true} or a {@code GENERATE_QUESTIONS}
   * command was applied — the run waits for
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime#submitInput(String, java.util.Map, String)}.
   */
  AWAITING_INPUT,

  /**
   * An {@code ESCALATE} command was applied — the run waits for
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime#approve(String, String, String, String)}.
   */
  AWAITING_APPROVAL,

  /**
   * A {@code TOOL_INVOCATION} requires human approval before execution (policy
   * {@code RequireApproval}) — the run waits for
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime#continueAfterToolApproval(String, String,
   * com.agentforge4j.core.spi.tool.ApprovalDecision)}.
   */
  AWAITING_TOOL_APPROVAL,

  /**
   * A {@code TOOL_INVOCATION} was denied by policy or failed after retries — the run waits for an
   * operator {@code ToolDecision} via
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime#resolveToolDecision(String, String,
   * com.agentforge4j.core.spi.tool.ToolDecision)}.
   */
  AWAITING_TOOL_DECISION;
}
