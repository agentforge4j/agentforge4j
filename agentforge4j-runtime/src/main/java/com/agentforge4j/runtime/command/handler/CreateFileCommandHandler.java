// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.CreateFileCommand;
import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.file.ArtifactDescriptor;
import com.agentforge4j.runtime.GeneratedArtifactStore;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Handles {@link CreateFileCommand} by forwarding content to {@link FileSink}, then — only once the
 * write succeeds and only when the path is in the run's capture set (the union of {@code VALIDATE}-declared
 * paths) — capturing the emitted bytes in the run-scoped {@link GeneratedArtifactStore} and recording a
 * content-free {@link ArtifactDescriptor} on the run state. A failed write throws before capture, so nothing
 * is registered for a file that was not written; a path no {@code VALIDATE} step declares is written but not
 * captured, so a run with no {@code VALIDATE} step captures nothing.
 */
public final class CreateFileCommandHandler implements CommandHandler<CreateFileCommand> {

  private final EventRecorder eventRecorder;
  private final FileSink fileSink;
  private final GeneratedArtifactStore generatedArtifactStore;

  private static final System.Logger LOG = System.getLogger(
      CreateFileCommandHandler.class.getName());

  /**
   * Creates a handler.
   *
   * @param eventRecorder          event sink for file creation side effects
   * @param fileSink               destination for file bytes
   * @param generatedArtifactStore run-scoped store the emitted bytes are captured into after a
   *                               successful write
   */
  public CreateFileCommandHandler(EventRecorder eventRecorder, FileSink fileSink,
      GeneratedArtifactStore generatedArtifactStore) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.fileSink = Validate.notNull(fileSink, "fileSink must not be null");
    this.generatedArtifactStore =
        Validate.notNull(generatedArtifactStore, "generatedArtifactStore must not be null");
  }

  @Override
  public Class<CreateFileCommand> getCommandClass() {
    return CreateFileCommand.class;
  }

  /** {@inheritDoc} */
  @Override
  public CommandApplicationResult apply(CreateFileCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "CreateFile command path={0}", cmd.path());

    String runId = request.state().getRunId();
    String stepId = request.state().getCurrentStepId();

    fileSink.write(runId, stepId, cmd.path(), cmd.content());

    // The external write succeeded. Capture the in-process emitted bytes and persist a content-free
    // descriptor, ordered strictly after the write so a failed write registers nothing — but only for a
    // path the run actually validates (the VALIDATE-declared capture set), so a workflow with no VALIDATE
    // step contributes nothing. If capture itself fails (configured bounds), the external file has already
    // been written and is intentionally not rolled back: record the partial-output condition and fail closed.
    if (request.state().getCapturedArtifactPaths().contains(cmd.path())) {
      captureWrittenArtifact(cmd, request, runId, stepId);
    }

    eventRecorder.record(runId,
        stepId,
        WorkflowEventType.CONTEXT_UPDATED,
        "created file: %s".formatted(cmd.path()),
        request.agentId());
    return CommandApplicationResult.CONTINUE;
  }

  private void captureWrittenArtifact(CreateFileCommand cmd, CommandApplicationRequest request,
      String runId, String stepId) {
    try {
      generatedArtifactStore.register(runId, stepId, cmd.path(), cmd.content());
      request.state().addGeneratedArtifactDescriptor(
          new ArtifactDescriptor(cmd.path(), sha256Hex(cmd.content()), stepId, request.currentStepUid()));
    } catch (RuntimeException captureFailure) {
      // The external file is already written; do not roll it back. Record that the write succeeded but
      // capture failed afterward (partial output), then fail the run closed with no descriptor added.
      String reason = ("External file '%s' was written by step '%s', but generated-artifact capture "
          + "failed afterward; the written file is not rolled back: %s")
          .formatted(cmd.path(), stepId, captureFailure.getMessage());
      eventRecorder.record(runId, stepId, WorkflowEventType.STEP_FAILED, reason, request.agentId());
      throw new StepExecutionException(reason, captureFailure);
    }
  }

  private static String sha256Hex(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
