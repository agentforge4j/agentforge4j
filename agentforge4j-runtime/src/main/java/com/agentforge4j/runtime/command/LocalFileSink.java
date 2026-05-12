package com.agentforge4j.runtime.command;

import com.agentforge4j.util.Validate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalFileSink implements FileSink {

  private final Path baseDir;

  public LocalFileSink(Path baseDir) {
    this.baseDir = Validate.notNull(baseDir, "baseDir must not be null");
  }

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
