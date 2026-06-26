// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
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
import com.agentforge4j.runtime.execution.behaviour.resource.SafeClasspathResourceResolver;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BranchContinuationRuntimeTest {

  @Test
  void branch_with_executable_runs_selected_branch_then_continues() {
    String goBranchKey = loadResourceValue("/examples/branch-go.txt");
    StepDefinition selector = resourceStep("selector", "/examples/branch-go.txt", "route");
    StepDefinition branchTarget = resourceStep("branch-target", "/examples/sample.txt",
        "branch.result");
    StepDefinition branch = branchStep("branch", "route", selectorMap(goBranchKey, branchTarget),
        null);
    StepDefinition after = resourceStep("after", "/workflow-resources/info.txt", "after.result");
    WorkflowDefinition workflow = workflow("wf-branch-executable", Map.of(),
        List.of(selector, branch, after));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(fixture.runtime().getState(runId).getContext()).containsKeys("branch.result",
        "after.result");
  }

  @Test
  void branch_with_null_default_continues_to_next_step() {
    StepDefinition selector = resourceStep("selector", "/examples/branch-miss.txt", "route");
    StepDefinition branch = branchStep("branch", "route",
        selectorMap("go", resourceStep("never", "/examples/sample.txt", "never")),
        null);
    StepDefinition after = resourceStep("after", "/workflow-resources/info.txt", "after.result");
    WorkflowDefinition workflow = workflow("wf-branch-null-default", Map.of(),
        List.of(selector, branch, after));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(fixture.runtime().getState(runId).getContext()).containsKey("after.result");
    assertThat(fixture.runtime().getState(runId).getContext()).doesNotContainKey("never");
  }

  @Test
  void branch_with_empty_executable_entry_continues() {
    String goBranchKey = loadResourceValue("/examples/branch-go.txt");
    StepDefinition selector = resourceStep("selector", "/examples/branch-go.txt", "route");
    Map<String, Executable> branches = new HashMap<>();
    branches.put(goBranchKey, null);
    StepDefinition branch = branchStep("branch", "route", branches, null);
    StepDefinition after = resourceStep("after", "/workflow-resources/info.txt", "after.result");
    WorkflowDefinition workflow = workflow("wf-branch-empty", Map.of(),
        List.of(selector, branch, after));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(fixture.runtime().getState(runId).getContext()).containsKey("after.result");
  }

  @Test
  void nested_branch_continuation_falls_through_correctly() {
    String goBranchKey = loadResourceValue("/examples/branch-go.txt");
    StepDefinition selector = resourceStep("selector", "/examples/branch-go.txt", "route");
    StepDefinition innerSelector = resourceStep("inner-selector", "/examples/branch-miss.txt",
        "inner.route");
    StepDefinition innerBranch = branchStep("inner-branch",
        "inner.route",
        selectorMap("hit", resourceStep("inner-never", "/examples/sample.txt", "inner.never")),
        null);
    StepDefinition outerBranch = branchStep("outer-branch",
        "route",
        selectorMap(goBranchKey, innerBranch),
        null);
    StepDefinition after = resourceStep("after", "/workflow-resources/info.txt", "after.result");
    WorkflowDefinition workflow = workflow("wf-branch-nested", Map.of(),
        List.of(selector, innerSelector, outerBranch, after));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(fixture.runtime().getState(runId).getContext()).containsKey("after.result");
    assertThat(fixture.runtime().getState(runId).getContext()).doesNotContainKey("inner.never");
  }

  @Test
  void branch_continuation_inside_blueprint_completes() {
    StepDefinition selector = resourceStep("selector", "/examples/branch-miss.txt", "route");
    StepDefinition branch = branchStep("branch",
        "route",
        selectorMap("hit", resourceStep("never", "/examples/sample.txt", "never")),
        null);
    StepDefinition insideBlueprint = resourceStep("bp-after", "/templates/template.txt",
        "bp.after");
    BlueprintDefinition blueprint = new BlueprintDefinition(
        "bp1",
        "branch blueprint",
        new BlueprintBehaviour(LoopConfig.withDefaults(
            LoopTerminationStrategy.FIXED_COUNT,
            null,
            null,
            1,
            null), StepTransition.AUTO),
        List.of(selector, branch, insideBlueprint));
    StepDefinition after = resourceStep("after", "/workflow-resources/info.txt", "after.result");
    WorkflowDefinition workflow = workflow(
        "wf-branch-blueprint",
        Map.of(blueprint.blueprintId(), blueprint),
        List.of(new BlueprintRef(blueprint.blueprintId()), after));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(fixture.runtime().getState(runId).getContext()).containsKeys("bp.after",
        "after.result");
  }

  @Test
  void retry_redrives_from_top_level_branch_so_downstream_reexecutes() {
    StepDefinition selector = resourceStep("selector", "/examples/branch-miss.txt", "route");
    StepDefinition branch = branchStep("branch",
        "route",
        selectorMap("go", resourceStep("never", "/examples/sample.txt", "never")),
        null);
    StepDefinition terminalFail = StepDefinition.builder()
        .withStepId("fail")
        .withName("fail")
        .withBehaviour(new FailBehaviour("expected"))
        .build();
    WorkflowDefinition workflow = workflow("wf-branch-retry", Map.of(),
        List.of(selector, branch, terminalFail));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "branch", "user");

    // Path A: retry re-drives from the top-level branch, so the branch is re-evaluated and the
    // downstream terminal fail step runs again — the run fails again on the real downstream outcome.
    // The pre-fix defect finalised COMPLETED after executing the branch alone, so a RUN_COMPLETED
    // event must never be emitted here.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    long runCompleted = fixture.eventLog().getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.RUN_COMPLETED)
        .count();
    assertThat(runCompleted).isZero();
  }

  @Test
  void audit_records_branch_decision_for_continuation_branch() {
    StepDefinition selector = resourceStep("selector", "/examples/branch-miss.txt", "route");
    StepDefinition branch = branchStep("branch",
        "route",
        selectorMap("go", resourceStep("never", "/examples/sample.txt", "never")),
        null);
    StepDefinition after = resourceStep("after", "/workflow-resources/info.txt", "after.result");
    WorkflowDefinition workflow = workflow("wf-branch-audit", Map.of(),
        List.of(selector, branch, after));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    List<WorkflowEvent> events = fixture.eventLog().getEvents(runId);
    assertThat(events.stream()
        .filter(event -> event.stepId() != null && event.stepId().equals("branch"))
        .filter(event -> event.eventType() == WorkflowEventType.STEP_STARTED)
        .anyMatch(event -> event.payload() != null && event.payload()
            .contains("selectedBranch='default'")))
        .isTrue();
  }

  private static Map<String, Executable> selectorMap(String key, Executable executable) {
    Map<String, Executable> branches = new HashMap<>();
    branches.put(key, executable);
    return branches;
  }

  private static StepDefinition resourceStep(String stepId, String resourcePath,
      String contextKey) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour(resourcePath, contextKey, StepTransition.AUTO))
        .build();
  }

  private static StepDefinition branchStep(String stepId,
      String contextKey,
      Map<String, Executable> branches,
      Executable defaultBranch) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new BranchBehaviour(contextKey, branches, List.of(), defaultBranch, false))
        .build();
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
        steps);
  }

  private static Fixture fixture(WorkflowDefinition workflow) {
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(resolver.resolve(any())).thenReturn(client);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    AgentRepository agentRepository = mock(AgentRepository.class);
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
    ObjectMapper mapper = new ObjectMapper();
    EventRecorder eventRecorder = new EventRecorder(eventLog, clock);
    AgentInvoker agentInvoker = AgentInvoker.builder()
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

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, eventLog);
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowEventLog eventLog) {

  }

  private static String loadResourceValue(String path) {
    return new SafeClasspathResourceResolver().resolve(path);
  }
}
