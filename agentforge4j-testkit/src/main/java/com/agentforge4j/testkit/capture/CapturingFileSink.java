// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import com.agentforge4j.runtime.command.FileSink;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link FileSink} that captures every write instead of touching disk, so a test can
 * assert which files a run produced.
 *
 * <p>Writes are captured verbatim — including any path-traversal attempt — because there is no
 * filesystem to protect; traversal rejection is exercised at the command layer, not here.
 */
public final class CapturingFileSink implements FileSink {

  private final List<CapturedFile> files = new CopyOnWriteArrayList<>();

  @Override
  public void write(String runId, String stepId, String path, String content) {
    files.add(new CapturedFile(runId, stepId, path, content));
  }

  /**
   * Returns the captured writes in the order they occurred.
   *
   * @return an immutable snapshot of the captured files
   */
  public List<CapturedFile> capturedFiles() {
    return List.copyOf(files);
  }
}
