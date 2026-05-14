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
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime#submitInput(String, java.util.Map)}.
   */
  AWAITING_INPUT,

  /**
   * An {@code ESCALATE} command was applied — the run waits for
   * {@link com.agentforge4j.core.runtime.WorkflowRuntime#approve(String, String, String)}.
   */
  AWAITING_APPROVAL;
}
