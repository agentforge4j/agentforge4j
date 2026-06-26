// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.CreateFileCommand;
import com.agentforge4j.core.exception.StepExecutionException;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.file.ArtifactDescriptor;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.runtime.InMemoryGeneratedArtifactStore;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateFileCommandHandlerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private static EventRecorder recorder() {
    return new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
  }

  private static WorkflowState state() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, CLOCK.instant());
    state.setCurrentStepId("generate");
    return state;
  }

  private static CommandApplicationRequest request(WorkflowState state) {
    return new CommandApplicationRequest(state, ContextMapping.none(), "agent-1", 1);
  }

  @Test
  void capture_eligible_write_captures_bytes_in_store_and_descriptor_in_state() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    FileSink sink = (runId, stepId, path, content) -> { /* accept */ };
    CreateFileCommandHandler handler = new CreateFileCommandHandler(recorder(), sink, store);
    WorkflowState state = state();
    state.mergeCapturedArtifactPaths(List.of("agent.json"));

    CommandApplicationResult result =
        handler.apply(new CreateFileCommand("agent.json", "{\"id\":\"a\"}"), request(state));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(store.find("run-1", "agent.json")).contains("{\"id\":\"a\"}");
    assertThat(state.getGeneratedArtifactDescriptors()).hasSize(1);
    ArtifactDescriptor descriptor = state.getGeneratedArtifactDescriptors().get(0);
    assertThat(descriptor.path()).isEqualTo("agent.json");
    assertThat(descriptor.stepId()).isEqualTo("generate");
    assertThat(descriptor.contentHash()).isNotBlank();
    assertThat(descriptor.stepExecutionUid()).isEqualTo(1);
  }

  @Test
  void path_not_in_capture_set_is_written_but_not_captured() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    FileSink sink = (runId, stepId, path, content) -> { /* accept */ };
    CreateFileCommandHandler handler = new CreateFileCommandHandler(recorder(), sink, store);
    // No VALIDATE-declared path merged into the run, so the write is not captured.
    WorkflowState state = state();

    CommandApplicationResult result =
        handler.apply(new CreateFileCommand("notes.md", "freeform"), request(state));

    assertThat(result).isEqualTo(CommandApplicationResult.CONTINUE);
    assertThat(store.find("run-1", "notes.md")).isEmpty();
    assertThat(state.getGeneratedArtifactDescriptors()).isEmpty();
  }

  @Test
  void failed_write_registers_nothing() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    FileSink failing = (runId, stepId, path, content) -> {
      throw new IllegalStateException("sink boom");
    };
    CreateFileCommandHandler handler = new CreateFileCommandHandler(recorder(), failing, store);
    WorkflowState state = state();
    state.mergeCapturedArtifactPaths(List.of("agent.json"));

    assertThatThrownBy(
        () -> handler.apply(new CreateFileCommand("agent.json", "x"), request(state)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sink boom");

    assertThat(store.find("run-1", "agent.json")).isEmpty();
    assertThat(state.getGeneratedArtifactDescriptors()).isEmpty();
  }

  @Test
  void constructor_rejects_null_store() {
    assertThatThrownBy(
        () -> new CreateFileCommandHandler(recorder(), FileSink.NO_OP_FILE_SINK, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("generatedArtifactStore");
  }

  @Test
  void same_path_re_emit_upserts_without_failure() {
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore();
    CreateFileCommandHandler handler =
        new CreateFileCommandHandler(recorder(), FileSink.NO_OP_FILE_SINK, store);
    WorkflowState state = state();
    state.mergeCapturedArtifactPaths(List.of("agent.json"));

    handler.apply(new CreateFileCommand("agent.json", "first"), request(state));
    // Re-emitting the same captured path is last-write-wins, not a failure.
    handler.apply(new CreateFileCommand("agent.json", "second"), request(state));

    assertThat(store.find("run-1", "agent.json")).contains("second");
    assertThat(state.getGeneratedArtifactDescriptors()).hasSize(1);
    assertThat(state.getGeneratedArtifactDescriptors().get(0).path()).isEqualTo("agent.json");
  }

  @Test
  void bounds_rejection_after_successful_write_fails_closed_with_partial_output_audit() {
    InMemoryWorkflowEventLog log = new InMemoryWorkflowEventLog();
    EventRecorder recorder = new EventRecorder(log, CLOCK);
    InMemoryGeneratedArtifactStore store = new InMemoryGeneratedArtifactStore(1, 1000);
    store.register("run-1", "generate", "first.json", "x");
    CreateFileCommandHandler handler =
        new CreateFileCommandHandler(recorder, FileSink.NO_OP_FILE_SINK, store);
    WorkflowState state = state();
    state.mergeCapturedArtifactPaths(List.of("second.json"));

    assertThatThrownBy(
        () -> handler.apply(new CreateFileCommand("second.json", "y"), request(state)))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("was written")
        .hasMessageContaining("capture failed")
        .hasMessageContaining("exceeded max generated artifacts");

    assertThat(state.getGeneratedArtifactDescriptors()).isEmpty();
    assertThat(partialOutputAudit(log))
        .contains("second.json")
        .contains("capture failed afterward");
  }

  private static String partialOutputAudit(InMemoryWorkflowEventLog log) {
    return log.getEvents("run-1").stream()
        .filter(event -> event.eventType() == WorkflowEventType.STEP_FAILED)
        .map(WorkflowEvent::payload)
        .reduce("", (a, b) -> a + b);
  }
}
