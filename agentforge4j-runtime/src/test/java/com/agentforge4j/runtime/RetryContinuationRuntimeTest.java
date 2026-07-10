// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the {@code retry} continuation contract (Path A): retrying a top-level step repositions the
 * run and re-drives the enclosing sequence so the target and every downstream step execute again, instead of finalising
 * the run after running the target alone. Nested targets are rejected fail-fast.
 */
class RetryContinuationRuntimeTest {

  @Test
  void retry_middle_step_redrives_downstream_and_does_not_complete_early() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    StepDefinition s3 = resourceStep("s3", "/templates/template.txt", "k3");
    StepDefinition terminalFail = failStep("fail");
    WorkflowDefinition workflow = workflow("wf-retry-middle", Map.of(),
        List.of(s1, s2, s3, terminalFail));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "s2", "user");

    // The downstream terminal fail step runs again, so the run fails again rather than completing
    // after executing s2 alone (the pre-fix defect finalised COMPLETED here).
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(countEvents(fixture, runId, null, WorkflowEventType.RUN_COMPLETED)).isZero();
  }

  @Test
  void retry_clears_stale_target_output_but_keeps_completed_upstream() {
    // Agent steps register a step output, so the resume replay-skip would reuse a stale output if it
    // were not cleared. This isolates the clearing: a1 (upstream of the target) is kept and skipped;
    // a2 (the target) is cleared and re-executes.
    StepDefinition a1 = agentStep("a1");
    StepDefinition a2 = agentStep("a2");
    StepDefinition terminalFail = failStep("fail");
    WorkflowDefinition workflow = workflow("wf-retry-stale", Map.of(),
        List.of(a1, a2, terminalFail));

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "a2", "user");

    assertThat(countEvents(fixture, runId, "a1", WorkflowEventType.STEP_STARTED)).isEqualTo(1);
    assertThat(countEvents(fixture, runId, "a2", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
  }

  @Test
  void retry_paused_run_clears_pending_suspension_and_redrives_to_completion() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-retry-paused", Map.of(), List.of(s1, s2));

    Fixture fixture = fixture(workflow);
    String runId = "paused-run";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.putStepOutput("s1", "stale");
    seeded.putStepExecutionUid("s1", 1);
    seeded.setCurrentStepId("s1");
    seeded.setPendingUserPrompt("waiting for a condition");
    seeded.setStatus(WorkflowStatus.PAUSED);
    fixture.stateRepository().save(seeded);

    fixture.runtime().retry(runId, "s1", "user");

    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(after.getPendingUserPrompt()).isNull();
    assertThat(after.getContext()).containsKeys("k1", "k2");
  }

  @Test
  void retry_blueprint_inner_step_is_rejected_with_enclosing_id() {
    StepDefinition inner = resourceStep("inner", "/examples/sample.txt", "inner.result");
    BlueprintDefinition blueprint = blueprint("bp1", List.of(inner));
    WorkflowDefinition workflow = workflow("wf-retry-bp",
        Map.of(blueprint.blueprintId(), blueprint),
        List.of(new BlueprintRef(blueprint.blueprintId())));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "inner", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a top-level retry target")
        .hasMessageContaining("retry the enclosing step 'bp1'");
  }

  @Test
  void retry_loop_body_step_is_rejected_with_enclosing_id() {
    StepDefinition loopStep = resourceStep("loop-step", "/examples/sample.txt", "loop.result");
    BlueprintDefinition loopBlueprint = blueprint("loop-bp", List.of(loopStep));
    WorkflowDefinition workflow = workflow("wf-retry-loop",
        Map.of(loopBlueprint.blueprintId(), loopBlueprint),
        List.of(new BlueprintRef(loopBlueprint.blueprintId())));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "loop-step", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retry the enclosing step 'loop-bp'");
  }

  @Test
  void retry_nested_workflow_step_is_rejected_with_enclosing_id() {
    StepDefinition nestedStep = resourceStep("nested-step", "/examples/sample.txt", "nested.result");
    WorkflowDefinition nested = workflow("nested-wf", Map.of(), List.of(nestedStep));
    WorkflowDefinition workflow = workflow("wf-retry-nested", Map.of(), List.of(nested));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "nested-step", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retry the enclosing step 'nested-wf'");
  }

  @Test
  void retry_past_gated_blueprint_reruns_and_regates_the_blueprint() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition b1 = resourceStep("b1", "/workflow-resources/info.txt", "k2");
    BlueprintDefinition gated = new BlueprintDefinition("bp-gated", "bp-gated",
        new BlueprintBehaviour(null, StepTransition.HUMAN_APPROVAL), List.of(b1));
    StepDefinition terminalFail = failStep("fail");
    WorkflowDefinition workflow = workflow("wf-retry-gated-bp",
        Map.of("bp-gated", gated),
        List.of(s1, new BlueprintRef("bp-gated"), terminalFail));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    fixture.runtime().decideStepApproval(runId, "bp-gated",
        new StepApprovalDecision.Approve("approver", "ok"));
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "s1", "user");

    // The rewind cleared the blueprint's body state, so the re-drive must re-run and re-gate the
    // blueprint — the pre-fix defect left the gate marker in place and silently skipped the
    // blueprint over its wiped body state.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    assertThat(after.getContext()).containsKey("k2");
    assertThat(countEvents(fixture, runId, "b1", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
  }

  @Test
  void retry_that_completes_clears_stale_failure_details() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-retry-clears-failure", Map.of(), List.of(s1, s2));

    Fixture fixture = fixture(workflow);
    String runId = "failed-with-details";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.setStatus(WorkflowStatus.FAILED);
    seeded.setRunFailure(new RunFailure.ExceptionFailure("boom", "s1", "support-1"));
    fixture.stateRepository().save(seeded);

    fixture.runtime().retry(runId, "s1", "user");

    // The failure details belong to the discarded attempt: a retried run that completes must not
    // keep reporting the dead attempt's reason/step/supportId.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(after.getRunFailure()).isNull();
    assertThat(after.getFailureReason()).isNull();
  }

  @Test
  void resume_drive_allocates_uids_above_persisted_ones() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-uid-monotonic", Map.of(), List.of(s1, s2));

    Fixture fixture = fixture(workflow);
    String runId = "paused-uid-run";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.putStepOutput("s1", "done");
    seeded.putStepExecutionUid("s1", 5);
    seeded.setCurrentStepId("s1");
    seeded.setStatus(WorkflowStatus.PAUSED);
    fixture.stateRepository().save(seeded);

    fixture.runtime().continueRun(runId, "user");

    // s2 executed on the resume drive; its uid must continue the run's ordering. Pre-fix the
    // counter restarted at 1 on every drive, colliding below s1's uid and breaking the rewind
    // range logic that clearEntriesFromUid applies on retry.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(after.getStepExecutionUid().get("s2")).isGreaterThan(5);
  }

  @Test
  void retry_of_cyclic_workflow_references_is_bounded_and_fails_cleanly() {
    // Programmatic definitions bypass load-time cycle validation, so a cyclic WORKFLOW reference
    // pair is constructible. Execution fails the run cleanly at the nesting guard; the retry rewind
    // walk must be bounded the same way rather than recursing the definition graph without limit.
    StepDefinition intoB = workflowStep("into-b", "wf-cycle-b");
    StepDefinition backToA = workflowStep("back-to-a", "wf-cycle-a");
    WorkflowDefinition wfA = workflow("wf-cycle-a", Map.of(), List.of(intoB));
    WorkflowDefinition wfB = workflow("wf-cycle-b", Map.of(), List.of(backToA));

    Fixture fixture = fixture(Map.of(wfA.id(), wfA, wfB.id(), wfB), defaultAgentInvoker());
    String runId = fixture.runtime().start(wfA.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "into-b", "user");

    // The retry completed normally (no StackOverflowError) and the re-drive failed again at the
    // same execution-time cycle guard.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void retry_with_a_cyclic_blueprint_reference_downstream_is_bounded() {
    // A self-referential blueprint body is likewise constructible programmatically. Execution
    // rejects it fail-fast (WorkflowTreeWalker's depth bound fires at the first drive), so a FAILED
    // run can only exist for such a definition via seeded or restored state — but the retry rewind
    // walk runs before the drive and must be bounded for it too, never overflowing the stack.
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition terminalFail = failStep("fail");
    BlueprintDefinition selfReferential = new BlueprintDefinition(
        "bp-self", "bp-self",
        new BlueprintBehaviour(null, StepTransition.AUTO),
        List.of(new BlueprintRef("bp-self")));
    WorkflowDefinition workflow = workflow("wf-cyclic-bp",
        Map.of("bp-self", selfReferential),
        List.of(s1, terminalFail, new BlueprintRef("bp-self")));

    Fixture fixture = fixture(workflow);
    assertThatThrownBy(() -> fixture.runtime().start(workflow.id()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("circular blueprint reference");

    String runId = seedFailedRun(fixture, workflow);

    // The bounded rewind walk completes; the re-drive then rejects the cyclic definition with the
    // same clean depth-bound error rather than a StackOverflowError.
    assertThatThrownBy(() -> fixture.runtime().retry(runId, "s1", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("circular blueprint reference");
  }

  @Test
  void retry_unknown_step_is_rejected_as_not_found() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    WorkflowDefinition workflow = workflow("wf-retry-unknown", Map.of(), List.of(s1));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "ghost", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found in workflow 'wf-retry-unknown'");
  }

  private static long countEvents(Fixture fixture, String runId, String stepId,
      WorkflowEventType type) {
    List<WorkflowEvent> events = fixture.eventLog().getEvents(runId);
    return events.stream()
        .filter(event -> event.eventType() == type)
        .filter(event -> stepId == null ? event.stepId() == null : stepId.equals(event.stepId()))
        .count();
  }

  private String seedFailedRun(Fixture fixture, WorkflowDefinition workflow) {
    String runId = "failed-" + workflow.id();
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.setStatus(WorkflowStatus.FAILED);
    fixture.stateRepository().save(seeded);
    return runId;
  }

  private static StepDefinition resourceStep(String stepId, String resourcePath, String contextKey) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour(resourcePath, contextKey, StepTransition.AUTO))
        .build();
  }

  private static StepDefinition agentStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new AgentBehaviour(stepId + "-agent", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
  }

  private static AgentInvoker continuingAgentInvoker() {
    AgentInvoker invoker = mock(AgentInvoker.class);
    AgentInvocationResult result = AgentInvocationResult.builder()
        .withRawResponse("agent-output")
        .withCommands(List.of(new ContinueCommand(null, null, null)))
        .build();
    when(invoker.invoke(any(), any(), any(), any(), any(), any())).thenReturn(result);
    return invoker;
  }

  private static StepDefinition failStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new FailBehaviour("expected"))
        .build();
  }

  private static StepDefinition workflowStep(String stepId, String workflowRef) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new WorkflowBehaviour(workflowRef, StepTransition.AUTO))
        .build();
  }

  private static BlueprintDefinition blueprint(String blueprintId, List<Executable> steps) {
    return new BlueprintDefinition(
        blueprintId,
        blueprintId,
        new BlueprintBehaviour(LoopConfig.withDefaults(
            LoopTerminationStrategy.FIXED_COUNT,
            null,
            null,
            1,
            null), StepTransition.AUTO),
        steps);
  }

  private static WorkflowDefinition workflow(String id,
      Map<String, BlueprintDefinition> blueprints,
      List<Executable> steps) {
    return new WorkflowDefinition(
        id,
        id,
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        blueprints,
        steps,
        List.of(),
        List.of());
  }

  private static Fixture fixture(WorkflowDefinition workflow) {
    return fixture(workflow, defaultAgentInvoker());
  }

  private static Fixture fixture(WorkflowDefinition workflow, AgentInvoker agentInvoker) {
    return fixture(Map.of(workflow.id(), workflow), agentInvoker);
  }

  private static Fixture fixture(Map<String, WorkflowDefinition> workflows,
      AgentInvoker agentInvoker) {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(workflows))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, stateRepository, eventLog);
  }

  private static AgentInvoker defaultAgentInvoker() {
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(resolver.resolve(any())).thenReturn(client);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    AgentRepository agentRepository = mock(AgentRepository.class);
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    ObjectMapper mapper = new ObjectMapper();
    return AgentInvoker.builder()
        .agentRepository(agentRepository)
        .llmClientResolver(resolver)
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
