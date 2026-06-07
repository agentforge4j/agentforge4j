package com.agentforge4j.runtime.command;

/**
 * Canonical {@link FileSink} that accepts writes but performs no I/O.
 *
 * <p>This is the default sink the runtime falls back to when no {@link FileSink} is configured:
 * {@code CreateFileCommand} output is silently discarded. The runtime never assumes a filesystem,
 * so this keeps the engine storage-agnostic until an embedding application supplies a real sink (for
 * example {@link LocalFileSink}, a database, or object storage).
 *
 * <p>Assembly layers that fall back to this sink are responsible for warning the operator that file
 * output is discarded (see {@code bootstrap}'s {@code ComponentDefaults}); this class itself is
 * intentionally silent to avoid duplicate warnings on every write.
 *
 * <p>The shared {@link FileSink#NO_OP_FILE_SINK} constant exposes a singleton instance of this
 * class.
 */
public final class NoOpFileSink implements FileSink {

  /**
   * Accepts the write and performs no I/O.
   *
   * @param runId   id of the owning run; ignored
   * @param stepId  id of the producing step; ignored
   * @param path    the path the agent requested; ignored
   * @param content the file content; discarded
   */
  @Override
  public void write(String runId, String stepId, String path, String content) {
    // Intentionally discards content; this sink performs no I/O.
  }
}
