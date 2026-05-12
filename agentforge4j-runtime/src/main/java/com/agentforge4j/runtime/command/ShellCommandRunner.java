package com.agentforge4j.runtime.command;

/**
 * Abstraction over the {@link com.agentforge4j.core.command.RunCommandCommand} side effect.
 *
 * <p>Shell execution is opt-in by design. An embedding application that does not
 * want its agents running shell commands simply does not register a {@code ShellCommandRunner} —
 * the runtime then rejects any {@code RUN_COMMAND} command with a clear error.
 */
public interface ShellCommandRunner {

  /**
   * Execute the given shell command on behalf of the given run.
   *
   * @param runId   id of the owning run — useful for scoping the working directory
   * @param command the command to execute
   * @return the captured stdout of the command
   */
  String run(String runId, String command);
}
