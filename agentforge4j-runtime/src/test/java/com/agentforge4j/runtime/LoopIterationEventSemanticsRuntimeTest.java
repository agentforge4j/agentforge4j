// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage: {@code LOOP_ITERATION_STARTED}/{@code LOOP_ITERATION_COMPLETED}
 * must reflect the real per-iteration lifecycle — {@code COMPLETED} only for an iteration that
 * genuinely completed, {@code STARTED} only once per genuinely-new iteration entry (not re-emitted
 * when a paused iteration is redriven to completion) — driven through the real runtime, asserting
 * exact event counts and order, not just absence of exceptions.
 */
class LoopIterationEventSemanticsRuntimeTest {

  private static final String BLUEPRINT_ID = "bp-loop";

  @Test
  void genuinelyCompletedIterationRecordsExactlyOneStartedAndOneCompleted() {
    StepDefinition body = resourceStep("body");
    LoopConfig loopConfig = fixedCount(1);
    WorkflowDefinition workflow = loopWorkflow("wf-single-complete", loopConfig, List.of(body), null);

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(loopEventTypes(fixture, runId))
        .containsExactly(WorkflowEventType.LOOP_ITERATION_STARTED,
            WorkflowEventType.LOOP_ITERATION_COMPLETED);
  }

  @Test
  void pausedThenResumedIterationRecordsOneStartedAndOneCompletedOnlyAfterResume() {
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    // Body = [INPUT (pauses first drive), RESOURCE (only ever runs on the resume drive)]. The INPUT
    // step's own completion is recorded via a direct stepOutputs write in handleUserAnswers (no fresh
    // execution uid on the resume drive that submits it), so a later body step that only executes on
    // resume is what proves the iteration event fires on the drive that actually finishes the
    // iteration, not the one that merely paused it.
    StepDefinition gate = StepDefinition.builder()
        .withStepId("input")
        .withName("input")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    StepDefinition after = resourceStep("after");
    LoopConfig loopConfig = fixedCount(1);
    WorkflowDefinition workflow =
        loopWorkflow("wf-pause-resume", loopConfig, List.of(gate, after), form);

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    // Drive 1: the input step pauses mid-iteration — STARTED only, no COMPLETED yet.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(loopEventTypes(fixture, runId))
        .containsExactly(WorkflowEventType.LOOP_ITERATION_STARTED);

    fixture.runtime().submitInput(runId, Map.of("field1", "the answer"), "user");

    // Resume completes the SAME iteration: no duplicate STARTED, exactly one COMPLETED now.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(loopEventTypes(fixture, runId))
        .containsExactly(WorkflowEventType.LOOP_ITERATION_STARTED,
            WorkflowEventType.LOOP_ITERATION_COMPLETED);
  }

  @Test
  void resumedIterationWhoseOnlyRemainingStepAllocatesNoNewUidStillRecordsCompleted() {
    // Body = [INPUT] only. On the resume drive that answers it, the resume-skip guard finds the
    // step's uid/output already recorded from the pausing drive and allocates nothing fresh — the
    // exact shape (unlike the INPUT+RESOURCE fixture above) where the iteration genuinely completes
    // without the resumed call itself advancing the uid counter at all.
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition gate = StepDefinition.builder()
        .withStepId("input")
        .withName("input")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    LoopConfig loopConfig = fixedCount(1);
    WorkflowDefinition workflow =
        loopWorkflow("wf-input-only-pause-resume", loopConfig, List.of(gate), form);

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(loopEventTypes(fixture, runId))
        .containsExactly(WorkflowEventType.LOOP_ITERATION_STARTED);

    fixture.runtime().submitInput(runId, Map.of("field1", "the answer"), "user");

    // Never duplicates STARTED, and records exactly one COMPLETED for the now-genuinely-finished
    // iteration, even though the resume call itself allocated no new step-execution uid.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(loopEventTypes(fixture, runId))
        .containsExactly(WorkflowEventType.LOOP_ITERATION_STARTED,
            WorkflowEventType.LOOP_ITERATION_COMPLETED);
  }

  @Test
  void failedIterationRecordsStartedButNeverCompleted() {
    StepDefinition body = StepDefinition.builder()
        .withStepId("fail")
        .withName("fail")
        .withBehaviour(new FailBehaviour("expected"))
        .build();
    LoopConfig loopConfig = fixedCount(1);
    WorkflowDefinition workflow = loopWorkflow("wf-iteration-fail", loopConfig, List.of(body), null);

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(loopEventTypes(fixture, runId))
        .containsExactly(WorkflowEventType.LOOP_ITERATION_STARTED);
  }

  @Test
  void threeDistinctCompletingIterationsRecordThreeStartedAndThreeCompletedInOrder() {
    StepDefinition body = resourceStep("body");
    LoopConfig loopConfig = fixedCount(3);
    WorkflowDefinition workflow = loopWorkflow("wf-three-iterations", loopConfig, List.of(body), null);

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    List<WorkflowEvent> loopEvents = loopEvents(fixture, runId);
    assertThat(loopEvents.stream().map(WorkflowEvent::eventType))
        .containsExactly(
            WorkflowEventType.LOOP_ITERATION_STARTED, WorkflowEventType.LOOP_ITERATION_COMPLETED,
            WorkflowEventType.LOOP_ITERATION_STARTED, WorkflowEventType.LOOP_ITERATION_COMPLETED,
            WorkflowEventType.LOOP_ITERATION_STARTED, WorkflowEventType.LOOP_ITERATION_COMPLETED);
    assertThat(loopEvents.stream().map(WorkflowEvent::payload))
        .containsExactly("iteration=1", "iteration=1", "iteration=2", "iteration=2",
            "iteration=3", "iteration=3");
  }

  private static StepDefinition resourceStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", stepId + ".out", StepTransition.AUTO))
        .build();
  }

  private static LoopConfig fixedCount(int maxIterations) {
    return LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, maxIterations, MaxIterationsAction.FAIL);
  }

  private static WorkflowDefinition loopWorkflow(String id, LoopConfig loopConfig,
      List<StepDefinition> bodySteps, ArtifactDefinition artifact) {
    BlueprintDefinition blueprint = new BlueprintDefinition(BLUEPRINT_ID, BLUEPRINT_ID,
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO), List.copyOf(bodySteps));
    Map<String, ArtifactDefinition> artifacts = artifact == null
        ? Map.of()
        : Map.of(artifact.id(), artifact);
    List<Executable> steps = List.of(new BlueprintRef(BLUEPRINT_ID));
    return new WorkflowDefinition(id, id, null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, artifacts,
        Map.of(BLUEPRINT_ID, blueprint), steps, List.of());
  }

  private static List<WorkflowEventType> loopEventTypes(Fixture fixture, String runId) {
    return loopEvents(fixture, runId).stream().map(WorkflowEvent::eventType).toList();
  }

  /**
   * All {@code LOOP_ITERATION_STARTED}/{@code LOOP_ITERATION_COMPLETED} events recorded against the
   * loop blueprint itself (the {@code stepId} field {@code AbstractLoopStrategy.executeIteration}
   * records against) — deliberately excludes {@code CompleteCommandHandler}'s separate
   * {@code LOOP_ITERATION_COMPLETED} emission (recorded against the specific agent step id, a
   * different mechanism, out of scope here since none of these fixtures use an AGENT_SIGNAL loop).
   */
  private static List<WorkflowEvent> loopEvents(Fixture fixture, String runId) {
    return fixture.eventLog().getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.LOOP_ITERATION_STARTED
            || event.eventType() == WorkflowEventType.LOOP_ITERATION_COMPLETED)
        .filter(event -> BLUEPRINT_ID.equals(event.stepId()))
        .toList();
  }

  private static Fixture fixture(WorkflowDefinition workflow) {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(unusedAgentInvoker())
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, stateRepository, eventLog);
  }

  private static AgentInvoker unusedAgentInvoker() {
    ObjectMapper mapper = new ObjectMapper();
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC));
    return AgentInvoker.builder()
        .agentRepository(mock(com.agentforge4j.core.agent.AgentRepository.class))
        .llmClientResolver(mock(LlmClientResolver.class))
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowStateRepository stateRepository,
                         WorkflowEventLog eventLog) {

  }
}
