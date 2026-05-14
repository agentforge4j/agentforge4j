package com.agentforge4j.runtime.command;

/**
 * Abstraction over the {@link com.agentforge4j.core.command.CreateFileCommand} side effect.
 *
 * <p>Keeping this as an interface lets the embedding application decide whether
 * files are written to disk, captured in memory, uploaded to object storage, or rejected entirely.
 * The runtime never assumes a filesystem.
 */
@FunctionalInterface
public interface FileSink {

  FileSink NO_OP_FILE_SINK = (runId, stepId, path, content) -> {
  };

  /**
   * Accept a file produced by an agent. Implementations are responsible for their own
   * path-traversal protection when the target is a real filesystem.
   *
   * @param runId   id of the owning run — useful for scoping outputs per run
   * @param stepId  id of the producing step
   * @param path    the path the agent requested
   * @param content the file content
   */
  void write(String runId, String stepId, String path, String content);
}
