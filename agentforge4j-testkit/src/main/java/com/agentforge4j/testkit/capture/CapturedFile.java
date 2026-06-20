// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.capture;

import com.agentforge4j.util.Validate;

/**
 * One file a run requested through the {@link CapturingFileSink}, recorded in memory.
 *
 * @param runId   id of the owning run
 * @param stepId  id of the producing step
 * @param path    the path the agent requested (captured verbatim, including any traversal attempt)
 * @param content the file content
 */
public record CapturedFile(String runId, String stepId, String path, String content) {

  /**
   * Validates the captured write.
   */
  public CapturedFile {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(path, "path must not be blank");
    Validate.notNull(content, "content must not be null");
  }
}
