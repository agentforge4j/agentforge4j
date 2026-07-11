// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * End-to-end transition-gate behaviour over the real {@link WorkflowRuntimeBuilder} graph. Uses {@code RESOURCE} and
 * {@code INPUT} steps (no LLM) to exercise the synchronous ({@code afterHandle}) and {@code handleUserAnswers}
 * completion points.
 */
class TransitionGateRuntimeIT {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"),
      ZoneOffset.UTC);

  private InMemoryWorkflowEventLog eventLog;

  private WorkflowRuntime runtime(WorkflowDefinition workflow) {
    eventLog = new InMemoryWorkflowEventLog();
    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(eventLog)
        .agentInvoker(mock(AgentInvoker.class))
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();
  }

  @Test
  void autoTransitionAdvancesWithoutGating() {
    WorkflowDefinition workflow = workflow("wf-auto", resourceStep("s1", StepTransition.AUTO));
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(eventTypes(runId)).doesNotContain(WorkflowEventType.STEP_AWAITING_REVIEW,
        WorkflowEventType.STEP_AWAITING_APPROVAL);
  }

  @Test
  void humanReviewSyncSuspendsThenSubmitReviewAdvances() {
    WorkflowDefinition workflow = workflow("wf-review",
        resourceStep("s1", StepTransition.HUMAN_REVIEW),
        resourceStep("s2", StepTransition.AUTO));
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.STEP_AWAITING_REVIEW);

    runtime.submitReview(runId, "s1", "looks good", "alice");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    WorkflowEvent reviewed = firstEvent(runId, WorkflowEventType.STEP_REVIEWED);
    assertThat(reviewed.actorId()).isEqualTo("alice");
    assertThat(reviewed.payload()).isEqualTo("looks good");
  }

  @Test
  void humanApprovalSyncSuspendsThenApproveAdvances() {
    WorkflowDefinition workflow = workflow("wf-approve",
        resourceStep("s1", StepTransition.HUMAN_APPROVAL),
        resourceStep("s2", StepTransition.AUTO));
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.STEP_AWAITING_APPROVAL);

    runtime.decideStepApproval(runId, "s1", new StepApprovalDecision.Approve("bob", "ship it"));

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    WorkflowEvent approved = firstEvent(runId, WorkflowEventType.STEP_APPROVED);
    assertThat(approved.actorId()).isEqualTo("bob");
    assertThat(approved.payload()).isEqualTo("ship it");
  }

  @Test
  void rejectFailsRunWithStepRejectionFailure() {
    WorkflowDefinition workflow = workflow("wf-reject",
        resourceStep("s1", StepTransition.HUMAN_APPROVAL));
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());
    runtime.decideStepApproval(runId, "s1", new StepApprovalDecision.Reject("bob", "not acceptable"));

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(runtime.getState(runId).getRunFailure())
        .isInstanceOf(RunFailure.StepRejectionFailure.class)
        .satisfies(failure -> {
          assertThat(failure.failureReason()).isEqualTo("not acceptable");
          assertThat(failure.failedStepId()).isEqualTo("s1");
          assertThat(failure.supportId()).isNotBlank();
        });
    WorkflowEvent rejected = firstEvent(runId, WorkflowEventType.STEP_REJECTED);
    assertThat(rejected.actorId()).isEqualTo("bob");
    assertThat(rejected.payload()).isEqualTo("not acceptable");
  }

  @Test
  void inputStepHumanApprovalSuspendsAfterSubmitInput() {
    ArtifactDefinition artifact = new ArtifactDefinition(
        "form", List.of(new TextArtifactItem("field", "Field", true, null)));
    StepDefinition inputStep = StepDefinition.builder()
        .withStepId("in")
        .withName("in")
        .withBehaviour(new InputBehaviour("form", StepTransition.HUMAN_APPROVAL))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf-input", "wf-input", null, null, null,
        null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form", artifact),
        Map.of(), List.of(inputStep), List.of(), List.of());
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    runtime.submitInput(runId, Map.of("field", "answer"), "carol");

    assertThat(runtime.getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.STEP_AWAITING_APPROVAL);
  }

  @Test
  void failBehaviourDoesNotGate() {
    StepDefinition failStep = StepDefinition.builder()
        .withStepId("boom")
        .withName("boom")
        .withBehaviour(new FailBehaviour("deliberate"))
        .build();
    WorkflowDefinition workflow = workflow("wf-fail", failStep);
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(eventTypes(runId)).doesNotContain(WorkflowEventType.STEP_AWAITING_REVIEW,
        WorkflowEventType.STEP_AWAITING_APPROVAL);
  }

  @Test
  void submitReviewOnWrongStatusNamesCorrectVerb() {
    WorkflowDefinition workflow = workflow("wf-x",
        resourceStep("s1", StepTransition.HUMAN_APPROVAL));
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());

    assertThatThrownBy(() -> runtime.submitReview(runId, "s1", "note", "alice"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("decideStepApproval");
  }

  @Test
  void decideStepApprovalOnWrongStatusNamesCorrectVerb() {
    WorkflowDefinition workflow = workflow("wf-y",
        resourceStep("s1", StepTransition.HUMAN_REVIEW));
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());

    assertThatThrownBy(
        () -> runtime.decideStepApproval(runId, "s1", new StepApprovalDecision.Approve("bob", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("submitReview");
  }

  @Test
  void gatedBlueprintSuspendsPostLoopThenResumeSkipsIt() {
    BlueprintDefinition blueprint = new BlueprintDefinition("bp", "bp",
        new BlueprintBehaviour(null, StepTransition.HUMAN_REVIEW),
        List.of(resourceStep("inner", StepTransition.AUTO)));
    WorkflowDefinition workflow = new WorkflowDefinition("wf-bp", "wf-bp", null, null, null, null,
        null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of("bp", blueprint),
        List.of(new BlueprintRef("bp"), resourceStep("after", StepTransition.AUTO)),
        List.of(), List.of());
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.STEP_AWAITING_REVIEW);

    runtime.submitReview(runId, "bp", "reviewed", "alice");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void submitReviewWithUnknownStepIdIsRejected() {
    WorkflowDefinition workflow = workflow("wf-review-badstep",
        resourceStep("s1", StepTransition.HUMAN_REVIEW));
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);

    assertThatThrownBy(() -> runtime.submitReview(runId, "not-the-step", "note", "alice"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not-the-step");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);
    assertThat(eventTypes(runId)).doesNotContain(WorkflowEventType.STEP_REVIEWED);
  }

  @Test
  void decideStepApprovalWithUnknownStepIdIsRejected() {
    WorkflowDefinition workflow = workflow("wf-approve-badstep",
        resourceStep("s1", StepTransition.HUMAN_APPROVAL));
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);

    assertThatThrownBy(() -> runtime.decideStepApproval(runId, "not-the-step",
        new StepApprovalDecision.Reject("bob", "nope")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not-the-step");

    assertThat(runtime.getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    assertThat(eventTypes(runId)).doesNotContain(WorkflowEventType.STEP_REJECTED);
  }

  @Test
  void blueprintGatedReviewWithUnknownStepIdIsRejected() {
    BlueprintDefinition blueprint = new BlueprintDefinition("bp", "bp",
        new BlueprintBehaviour(null, StepTransition.HUMAN_REVIEW),
        List.of(resourceStep("inner", StepTransition.AUTO)));
    WorkflowDefinition workflow = new WorkflowDefinition("wf-bp-badstep", "wf-bp-badstep", null, null,
        null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(),
        Map.of("bp", blueprint),
        List.of(new BlueprintRef("bp"), resourceStep("after", StepTransition.AUTO)),
        List.of(), List.of());
    WorkflowRuntime runtime = runtime(workflow);
    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);

    assertThatThrownBy(() -> runtime.submitReview(runId, "not-the-blueprint", "note", "alice"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not-the-blueprint");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);
  }

  @Test
  void gatedResourceStepIsSkippedNotReExecutedOnResume() {
    WorkflowDefinition workflow = workflow("wf-resume-skip",
        resourceStep("s1", StepTransition.HUMAN_REVIEW),
        resourceStep("s2", StepTransition.AUTO));
    WorkflowRuntime runtime = runtime(workflow);

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);
    // RESOURCE records no natural step output; the gate marks it so the drive loop skips it on resume.
    assertThat(runtime.getState(runId).getStepOutputs()).containsEntry("s1", "gated:HUMAN_REVIEW");

    runtime.submitReview(runId, "s1", "ok", "alice");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(stepStartedCount(runId, "s1")).isEqualTo(1L); // skipped on resume, not re-run
    assertThat(countEvents(runId, WorkflowEventType.STEP_AWAITING_REVIEW)).isEqualTo(1L);
  }

  @Test
  void gatedWorkflowCarrierStepIsSkippedNotReExecutedOnResume() {
    WorkflowDefinition nested = workflow("wf-inner", resourceStep("inner", StepTransition.AUTO));
    StepDefinition callNested = StepDefinition.builder()
        .withStepId("call")
        .withName("call")
        .withBehaviour(new WorkflowBehaviour("wf-inner", StepTransition.HUMAN_REVIEW))
        .build();
    WorkflowDefinition parent = new WorkflowDefinition("wf-outer", "wf-outer", null, null, null,
        null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(callNested, resourceStep("after", StepTransition.AUTO)),
        List.of(), List.of());
    WorkflowRuntime runtime = runtime(Map.of(parent.id(), parent, nested.id(), nested));

    String runId = runtime.start(parent.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_REVIEW);
    // currentStepId is canonicalised to the workflow-carrier step despite nested execution.
    assertThat(runtime.getState(runId).getCurrentStepId()).isEqualTo("call");

    runtime.submitReview(runId, "call", "ok", "alice");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(stepStartedCount(runId, "inner")).isEqualTo(1L); // nested body not re-run on resume
  }

  @Test
  void nestedWorkflowInputProjectsDeclaredOutputKeysForADownstreamBranch() {
    // Sub-workflow: an INPUT step declaring outputKeys=[confirmed], then a BRANCH that reads the
    // bare context key "confirmed". The INPUT step lives in a nested sub-workflow frame, not the
    // root — its declared output keys must still be resolved and the answer projected under the
    // bare key, or the branch fails with "requires context key 'confirmed' but it is missing".
    ArtifactDefinition form = new ArtifactDefinition(
        "form", List.of(new TextArtifactItem("confirmed", "Confirmed", true, null)));
    StepDefinition collect = StepDefinition.builder()
        .withStepId("collect")
        .withName("collect")
        .withBehaviour(new InputBehaviour("form", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of("confirmed")))
        .build();
    StepDefinition rejected = StepDefinition.builder()
        .withStepId("rejected").withName("rejected")
        .withBehaviour(new FailBehaviour("not confirmed")).build();
    StepDefinition gate = StepDefinition.builder()
        .withStepId("gate").withName("gate")
        .withBehaviour(new BranchBehaviour("confirmed", Map.of("false", rejected), List.of(), null, false))
        .build();
    WorkflowDefinition sub = new WorkflowDefinition("wf-sub", "wf-sub", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form", form), Map.of(),
        List.of(collect, gate), List.of(), List.of());
    StepDefinition invoke = StepDefinition.builder()
        .withStepId("invoke").withName("invoke")
        .withBehaviour(new WorkflowBehaviour("wf-sub", StepTransition.AUTO)).build();
    WorkflowDefinition parent = workflow("wf-parent", invoke);
    WorkflowRuntime runtime = runtime(Map.of(parent.id(), parent, sub.id(), sub));

    String runId = runtime.start("wf-parent");
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    runtime.submitInput(runId, Map.of("confirmed", "true"), "tester");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(runtime.getState(runId).getContext()).containsKey("confirmed");
  }

  private long countEvents(String runId, WorkflowEventType type) {
    return eventLog.getEvents(runId).stream().filter(event -> event.eventType() == type).count();
  }

  private long stepStartedCount(String runId, String stepId) {
    return eventLog.getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.STEP_STARTED)
        .filter(event -> stepId.equals(event.stepId()))
        .count();
  }

  private WorkflowRuntime runtime(Map<String, WorkflowDefinition> workflows) {
    eventLog = new InMemoryWorkflowEventLog();
    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(workflows))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(eventLog)
        .agentInvoker(mock(AgentInvoker.class))
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();
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

  private static StepDefinition resourceStep(String stepId, StepTransition transition) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", stepId + ".ctx", transition))
        .build();
  }

  private static WorkflowDefinition workflow(String id, Executable... steps) {
    return new WorkflowDefinition(id, id, null, null, null, null, null, WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(steps),
        List.of(), List.of());
  }
}
