// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.file;

import com.agentforge4j.util.Validate;

/**
 * Durable, content-free record of a file a step emitted during a run, persisted on
 * {@link com.agentforge4j.core.workflow.state.WorkflowState}. Carries the requested {@code path}, a {@code contentHash}
 * of the emitted bytes, and the producing {@code stepId} — never the bytes themselves. The transient bytes live only in
 * the run-scoped generated-artifact store; this descriptor is the persisted record that survives a suspend/resume.
 *
 * @param path             non-blank path the agent requested for the artifact
 * @param contentHash      non-blank hash (hex) of the emitted content
 * @param stepId           non-blank id of the step that emitted the artifact
 * @param stepExecutionUid non-negative execution uid of the emitting step, so a retry/rewind can drop
 *                         descriptors at or after the rewind threshold (mirrors {@code stepOutputs})
 */
public record ArtifactDescriptor(String path, String contentHash, String stepId,
    int stepExecutionUid) {

  public ArtifactDescriptor {
    Validate.notBlank(path, "ArtifactDescriptor path must not be blank");
    Validate.notBlank(contentHash, "ArtifactDescriptor contentHash must not be blank");
    Validate.notBlank(stepId, "ArtifactDescriptor stepId must not be blank");
    Validate.isNotNegative(stepExecutionUid, "ArtifactDescriptor stepExecutionUid must not be negative");
  }
}
