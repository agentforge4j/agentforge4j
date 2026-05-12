package com.agentforge4j.runtime.command;

/**
 * Outcome of applying a batch of {@link com.agentforge4j.core.command.LlmCommand}s.
 *
 * <p>Behaviour handlers map this to the internal execution outcome type using
 * {@code CommandApplicationResults.toExecutionOutcome(...)} so this exported API stays decoupled
 * from non-exported execution packages.
 */
public enum CommandApplicationResult {

  /**
   * All commands applied, no special control-flow signal.
   */
  CONTINUE,

  /**
   * A {@code COMPLETE} command was seen — the enclosing loop iteration is done.
   */
  COMPLETE_SIGNAL,

  /**
   * A {@code USER_PROMPT(responseRequired=true)} or {@code GENERATE_QUESTIONS} was seen.
   */
  AWAITING_INPUT,

  /**
   * An {@code ESCALATE} command was seen.
   */
  AWAITING_APPROVAL;
}
