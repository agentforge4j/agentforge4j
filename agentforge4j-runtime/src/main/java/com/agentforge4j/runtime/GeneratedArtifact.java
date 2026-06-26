// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.util.Validate;

/**
 * A file emitted during a run and held transiently in a {@link GeneratedArtifactStore}: the producing {@code stepId},
 * the requested {@code path}, and the emitted {@code content}. Unlike the persisted
 * {@link com.agentforge4j.core.workflow.file.ArtifactDescriptor}, this carries the bytes and lives only for the
 * duration the store retains the run.
 *
 * @param stepId  non-blank id of the step that emitted the artifact
 * @param path    non-blank path the agent requested
 * @param content emitted content; must not be {@code null} (may be empty)
 */
public record GeneratedArtifact(String stepId, String path, String content) {

  public GeneratedArtifact {
    Validate.notBlank(stepId, "GeneratedArtifact stepId must not be blank");
    Validate.notBlank(path, "GeneratedArtifact path must not be blank");
    Validate.notNull(content, "GeneratedArtifact content must not be null");
  }
}
