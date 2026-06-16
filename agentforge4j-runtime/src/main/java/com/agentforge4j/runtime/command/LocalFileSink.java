// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import com.agentforge4j.util.Validate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link FileSink} that writes under {@code baseDir}, namespacing paths by {@code runId} and
 * rejecting paths that escape the base directory.
 */
public final class LocalFileSink implements FileSink {

  private final Path baseDir;

  /**
   * Creates a sink rooted at {@code baseDir}.
   *
   * @param baseDir root directory for all runs; relative paths use the default file system rules
   */
  public LocalFileSink(Path baseDir) {
    this.baseDir = Validate.notNull(baseDir, "baseDir must not be null");
  }

  /**
   * Writes UTF-8 text to {@code baseDir}/{@code runId}/{@code path}, creating parent directories as
   * needed.
   *
   * @throws IllegalArgumentException if {@code runId}, {@code stepId}, or {@code path} is blank, or
   *                                  the resolved path escapes {@code baseDir}
   * @throws IllegalStateException    if the file cannot be written
   */
  @Override
  public void write(String runId, String stepId, String path, String content) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(stepId, "stepId must not be blank");
    Validate.notBlank(path, "path must not be blank");
    Validate.notNull(content, "content must not be null");
    Path target = Validate.requireWithinBase(
        baseDir,
        runId + "/" + path,
        "Path escapes base directory: %s/%s".formatted(runId, path));

    try {
      Files.createDirectories(target.getParent());
      Files.writeString(target, content);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write file: %s".formatted(target), e);
    }
  }
}
