package com.agentforge4j.runtime.command;

/**
 * Abstraction over the {@link com.agentforge4j.core.command.RunCommandCommand} side effect.
 *
 * <p>Shell execution is opt-in by design. An embedding application that does not
 * want its agents running shell commands simply does not register a {@code ShellCommandRunner} —
 * the runtime then rejects any {@code RUN_COMMAND} command with a clear error.
 *
 * <p><strong>Sandboxing is entirely the embedder's responsibility.</strong> Implementations
 * receive <em>unvalidated, LLM-produced command strings</em>. The runtime does not sanitize,
 * parse, or restrict the command before calling {@link #run(String, String)}.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>command allow-listing or denylisting</li>
 *   <li>argument escaping</li>
 *   <li>working-directory restriction</li>
 *   <li>filesystem and network sandboxing</li>
 *   <li>resource limits (CPU, memory, wall-clock)</li>
 *   <li>output size capping</li>
 *   <li>never executing under elevated privileges</li>
 * </ul>
 *
 * <p>A default no-op or "reject all" implementation is acceptable for embedders that do not
 * intend to support shell execution (see {@link #NO_OP_SHELL_COMMAND_RUNNER}).
 *
 * <p>Implementations must not log the full command at INFO or above without considering that it
 * may contain secrets injected via workflow context.
 */
@FunctionalInterface
public interface ShellCommandRunner {

  /** Runs no process and returns an empty string. */
  ShellCommandRunner NO_OP_SHELL_COMMAND_RUNNER = (runId, command) -> "";

  /**
   * Execute the given shell command on behalf of the given run.
   *
   * <p>The {@code command} string is passed through from agent output without runtime validation.
   * See the interface Javadoc for embedder sandbox obligations.
   *
   * @param runId   id of the owning run — useful for scoping the working directory
   * @param command the command to execute
   * @return the captured stdout of the command
   */
  String run(String runId, String command);
}
