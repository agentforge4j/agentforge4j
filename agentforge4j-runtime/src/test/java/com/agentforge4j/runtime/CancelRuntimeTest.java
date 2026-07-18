// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.agentforge4j.runtime.tool.InMemoryPendingToolInvocationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Lifecycle coverage for {@link WorkflowRuntime#cancel(String, String)} over the real
 * {@link WorkflowRuntimeBuilder} graph: cancelling a suspended run marks it CANCELLED and emits
 * {@code RUN_CANCELLED}; every later run-mutating verb — continueRun, retry, approve, submitInput,
 * submitReview, decideStepApproval, continueAfterToolApproval, and resolveToolDecision — is
 * rejected via the cancellation guard; a repeated cancel is idempotent; terminal COMPLETED/FAILED
 * runs cannot be cancelled.
 */
class CancelRuntimeTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"),
      ZoneOffset.UTC);

  private InMemoryWorkflowEventLog eventLog;

  private WorkflowRuntime runtime(WorkflowDefinition workflow) {
    eventLog = new InMemoryWorkflowEventLog();
    // The tool wiring exists only so the two tool-resume verbs reach the cancellation guard
    // (they reject an unconfigured service before ever loading the run); the guard fires before
    // either collaborator is used.
    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(eventLog)
        .agentInvoker(mock(AgentInvoker.class))
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .toolExecutionService(mock(ToolExecutionService.class))
        .pendingToolInvocationStore(new InMemoryPendingToolInvocationStore())
        .build();
  }

  @Test
  void cancelWhileSuspendedAtInputMarksCancelledAndEmitsRunCancelled() {
    WorkflowRuntime runtime = runtime(inputWorkflow());
    String runId = runtime.start("wf-cancel-input");
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    runtime.cancel(runId, "alice");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    WorkflowEvent cancelled = firstEvent(runId, WorkflowEventType.RUN_CANCELLED);
    assertThat(cancelled.actorId()).isEqualTo("alice");
  }

  @Test
  void everyResumeVerbIsRejectedAfterCancel() {
    WorkflowRuntime runtime = runtime(inputWorkflow());
    String runId = runtime.start("wf-cancel-input");
    runtime.cancel(runId, "alice");

    assertThatThrownBy(() -> runtime.submitInput(runId, Map.of("field", "late"), "bob"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.continueRun(runId, "bob"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.retry(runId, "in", "bob"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.submitReview(runId, "in", "note", "bob"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.decideStepApproval(runId, "in",
        new StepApprovalDecision.Approve("bob", "ok")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.approve(runId, "in", "note", "bob"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.continueAfterToolApproval(runId, "tid-1",
        new ApprovalDecision.Approve("bob")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    assertThatThrownBy(() -> runtime.resolveToolDecision(runId, "tid-1",
        new ToolDecision.Continue("bob")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");
    // No state mutation and no resume happened: still CANCELLED, no late input in context.
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(runtime.getState(runId).getContext()).doesNotContainKey("form.field");
  }

  @Test
  void repeatedCancelIsIdempotentAndEmitsOneEvent() {
    WorkflowRuntime runtime = runtime(inputWorkflow());
    String runId = runtime.start("wf-cancel-input");
    runtime.cancel(runId, "alice");

    assertThatCode(() -> runtime.cancel(runId, "alice")).doesNotThrowAnyException();

    long cancelledEvents = eventLog.getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.RUN_CANCELLED)
        .count();
    assertThat(cancelledEvents).isEqualTo(1);
  }

  @Test
  void completedRunCannotBeCancelled() {
    WorkflowDefinition workflow = workflow("wf-cancel-done",
        resourceStep("s1", StepTransition.AUTO));
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

    assertThatThrownBy(() -> runtime.cancel(runId, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot cancel");
    assertThat(eventTypes(runId)).doesNotContain(WorkflowEventType.RUN_CANCELLED);
  }

  @Test
  void failedRunCannotBeCancelled() {
    StepDefinition failStep = StepDefinition.builder()
        .withStepId("boom")
        .withName("boom")
        .withBehaviour(new FailBehaviour("deliberate"))
        .build();
    WorkflowDefinition workflow = workflow("wf-cancel-failed", failStep);
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    assertThatThrownBy(() -> runtime.cancel(runId, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot cancel");
    assertThat(eventTypes(runId)).doesNotContain(WorkflowEventType.RUN_CANCELLED);
  }

  private List<WorkflowEventType> eventTypes(String runId) {
    return eventLog.getEvents(runId).stream().map(WorkflowEvent::eventType).toList();
  }

  private WorkflowEvent firstEvent(String runId, WorkflowEventType type) {
    return eventLog.getEvents(runId).stream()
        .filter(event -> event.eventType() == type)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No %s event for run %s".formatted(type, runId)));
  }

  private static WorkflowDefinition inputWorkflow() {
    ArtifactDefinition artifact = new ArtifactDefinition(
        "form", List.of(new TextArtifactItem("field", "Field", true, null)));
    StepDefinition inputStep = StepDefinition.builder()
        .withStepId("in")
        .withName("in")
        .withBehaviour(new InputBehaviour("form", StepTransition.AUTO))
        .build();
    return new WorkflowDefinition("wf-cancel-input", "wf-cancel-input", null, null, null, null,
        null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form", artifact), Map.of(),
        List.of(inputStep), List.of());
  }

  private static StepDefinition resourceStep(String stepId, StepTransition transition) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", stepId + ".ctx", transition))
        .build();
  }

  private static WorkflowDefinition workflow(String id, Executable... steps) {
    return new WorkflowDefinition(id, id, null, null, null, null, null, WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(steps), List.of());
  }
}
