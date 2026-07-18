// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.requirement.DefaultRequirementResolver;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AssignContextBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage: {@code CollectionBehaviour} ({@code COLLECTION}) has no registered
 * runtime {@code BehaviourHandler} in this release (the completion is planned for a later
 * milestone, ADR-0014 / #19). {@link WorkflowRuntimeBuilder#build()} rejects such a definition up
 * front as early feedback, reusing {@code agentforge4j-config-loader}'s bounded tree walker so the
 * check covers every structural nesting form — blueprint bodies, branch children, retry fallbacks,
 * inline nested workflows; the per-form traversal is pinned directly by
 * {@code WorkflowValidatorTest} in {@code agentforge4j-config-loader}, while this class exercises
 * the blueprint and sub-workflow forms end to end — as well as every workflow registered in the repository (including one
 * reachable only via another workflow's {@code WorkflowBehaviour} reference) — but {@code build()}
 * only ever sees a snapshot taken at construction time, so it is not the actual enforcement point.
 * A dynamic or hot-reloadable {@link com.agentforge4j.core.workflow.repository.WorkflowRepository}
 * can return a different, newly-broken definition later: {@code start()} re-validates the live
 * registry before any run state or {@code RUN_STARTED} event exists, and
 * {@code WorkflowBehaviourHandler} re-validates the specific nested workflow it is about to enter,
 * before any of that nested workflow's own steps execute.
 */
class WorkflowRuntimeCollectionGateTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void build_rejectsTopLevelCollectionStep_beforeAnyRunCanStart() {
    WorkflowDefinition workflow = workflow("wf-collection",
        Map.of(), List.of(collectionStep("collect1")));

    assertThatThrownBy(() -> builderFor(workflow).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("collect1")
        .hasMessageContaining("wf-collection")
        .hasMessageContaining("CollectionBehaviour");
  }

  @Test
  void build_rejectsCollectionStepNestedInsideBlueprint() {
    BlueprintDefinition blueprintWithCollection = new BlueprintDefinition(
        "bp-collect", "bp-collect", new BlueprintBehaviour(null, StepTransition.AUTO),
        List.of(collectionStep("collect-in-bp")));
    WorkflowDefinition workflow = workflow("wf-collection-blueprint",
        Map.of("bp-collect", blueprintWithCollection), List.of(new BlueprintRef("bp-collect")));

    assertThatThrownBy(() -> builderFor(workflow).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("collect-in-bp")
        .hasMessageContaining("wf-collection-blueprint");
  }

  @Test
  void build_rejectsCollectionStepInWorkflowBehaviourReachableSubWorkflow() {
    WorkflowDefinition sub = workflow("wf-sub-collection", Map.of(),
        List.of(collectionStep("collect-in-sub")));
    StepDefinition invokeSub = StepDefinition.builder()
        .withStepId("invoke-sub")
        .withName("invoke-sub")
        .withBehaviour(new WorkflowBehaviour(sub.id(), StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = workflow("wf-root", Map.of(), List.of(invokeSub));

    assertThatThrownBy(() -> new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(root.id(), root, sub.id(), sub)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .agentInvoker(unusedAgentInvoker())
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("collect-in-sub")
        .hasMessageContaining("wf-sub-collection");
  }

  @Test
  void build_ordinaryWorkflowUsingOtherBehaviours_stillBuildsAndRunsSuccessfully() {
    StepDefinition branch = StepDefinition.builder()
        .withStepId("route")
        .withName("route")
        .withBehaviour(new BranchBehaviour("choice",
            Map.of("go", resourceStep("resource-step", "go.executed")), List.of(), null, true))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition workflow = workflow("wf-ordinary", Map.of(), List.of(
        assignChoiceStep(), branch));

    WorkflowRuntime runtime = builderFor(workflow).build();
    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(runtime.getState(runId).getContext()).containsKey("go.executed");
  }

  @Test
  void start_rejectsWhenTheLiveRepositoryReturnsATopLevelCollectionWorkflowAtStartTime() {
    // Clean at build() time...
    WorkflowDefinition clean = workflow("wf-dynamic-collection", Map.of(),
        List.of(resourceStep("s1", "s1.out")));
    InMemoryWorkflowRepository repository =
        new InMemoryWorkflowRepository(Map.of(clean.id(), clean));
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    CapturingEventLog eventLog = new CapturingEventLog();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(repository)
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(unusedAgentInvoker())
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    // ...but the live registry now returns a COLLECTION-bearing definition for the same id by the
    // time start() is actually called.
    WorkflowDefinition broken = workflow("wf-dynamic-collection", Map.of(),
        List.of(collectionStep("collect1")));
    repository.replace(Map.of(broken.id(), broken));

    assertThatThrownBy(() -> runtime.start(broken.id()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("collect1")
        .hasMessageContaining("CollectionBehaviour");

    // Fails before any run state exists and before any event — RUN_STARTED included — is appended
    // for any run id (the log is runId-keyed and start()'s run id is minted internally, so only a
    // capture of every append can prove this).
    assertThat(stateRepository.findAll()).isEmpty();
    assertThat(eventLog.appended()).isEmpty();
  }

  @Test
  void start_rejectsWhenAWorkflowBehaviourReachableSubWorkflowChangesToCollectionAfterBuild() {
    WorkflowDefinition cleanSub = workflow("wf-dynamic-sub", Map.of(),
        List.of(resourceStep("sub-s1", "sub-s1.out")));
    StepDefinition invokeSub = StepDefinition.builder()
        .withStepId("invoke-sub")
        .withName("invoke-sub")
        .withBehaviour(new WorkflowBehaviour(cleanSub.id(), StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = workflow("wf-dynamic-root", Map.of(), List.of(invokeSub));
    InMemoryWorkflowRepository repository = new InMemoryWorkflowRepository(
        Map.of(root.id(), root, cleanSub.id(), cleanSub));
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    CapturingEventLog eventLog = new CapturingEventLog();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(repository)
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(unusedAgentInvoker())
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    // The root workflow itself stays clean; only the sub-workflow it references changes to contain
    // a COLLECTION step, between build() and start().
    WorkflowDefinition brokenSub = workflow("wf-dynamic-sub", Map.of(),
        List.of(collectionStep("collect-in-sub")));
    repository.replace(Map.of(root.id(), root, brokenSub.id(), brokenSub));

    assertThatThrownBy(() -> runtime.start(root.id()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("collect-in-sub")
        .hasMessageContaining("wf-dynamic-sub");

    assertThat(stateRepository.findAll()).isEmpty();
    assertThat(eventLog.appended()).isEmpty();
  }

  @Test
  void workflowBehaviourHandler_rejectsWhenTheNestedWorkflowChangesToCollectionAfterStartAlreadyPaused() {
    // The root pauses on an INPUT step before ever reaching the WORKFLOW step, so the mutation below
    // happens strictly between start() (which already validated a clean sub-workflow) and the later
    // resume that actually reaches WorkflowBehaviourHandler — proving this is a distinct enforcement
    // point from start()'s own registry-wide check, not a coincidental re-check of the same call.
    ArtifactDefinition form = new ArtifactDefinition("gate-form",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition gate = StepDefinition.builder()
        .withStepId("gate")
        .withName("gate")
        .withBehaviour(new InputBehaviour("gate-form", StepTransition.AUTO))
        .build();
    WorkflowDefinition cleanSub = workflow("wf-late-sub", Map.of(),
        List.of(resourceStep("sub-s1", "sub-s1.out")));
    StepDefinition invokeSub = StepDefinition.builder()
        .withStepId("invoke-sub")
        .withName("invoke-sub")
        .withBehaviour(new WorkflowBehaviour(cleanSub.id(), StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
    WorkflowDefinition root = new WorkflowDefinition(
        "wf-late-root", "wf-late-root", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("gate-form", form), Map.of(),
        List.of(gate, invokeSub), List.of());
    InMemoryWorkflowRepository repository = new InMemoryWorkflowRepository(
        Map.of(root.id(), root, cleanSub.id(), cleanSub));
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(repository)
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .agentInvoker(unusedAgentInvoker())
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    String runId = runtime.start(root.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    WorkflowDefinition brokenSub = workflow("wf-late-sub", Map.of(),
        List.of(collectionStep("collect-in-late-sub")));
    repository.replace(Map.of(root.id(), root, brokenSub.id(), brokenSub));

    runtime.submitInput(runId, Map.of("field1", "answer"), "user");

    // The exception is thrown from inside the drive, not to this caller: it is caught and recorded
    // as a run failure, same as any other step-level exception.
    WorkflowState after = runtime.getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(after.getRunFailure().failureReason())
        .contains("collect-in-late-sub")
        .contains("CollectionBehaviour");
  }

  @Test
  void start_rejectsWhenGetAndFindAllDisagreeAndGetsOwnResultCarriesTheCollectionStep() {
    // A WorkflowRepository whose get() and findAll() genuinely disagree for the same id — get()
    // (what start() actually drives) returns a COLLECTION-bearing definition, while findAll()'s
    // separate snapshot for that same id is clean. Validating only findAll() would miss this
    // entirely; start() must validate the exact object it retrieved via get().
    WorkflowDefinition brokenAsRetrievedByGet = workflow("wf-divergent", Map.of(),
        List.of(collectionStep("collect1")));
    WorkflowDefinition cleanAsSeenByFindAll = workflow("wf-divergent", Map.of(),
        List.of(resourceStep("s1", "s1.out")));
    WorkflowRepository divergent = new WorkflowRepository() {
      @Override
      public WorkflowDefinition get(String id) {
        return brokenAsRetrievedByGet;
      }

      @Override
      public Map<String, WorkflowDefinition> findAll() {
        return Map.of(cleanAsSeenByFindAll.id(), cleanAsSeenByFindAll);
      }
    };
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    CapturingEventLog eventLog = new CapturingEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);
    DefaultWorkflowRuntime runtime = new DefaultWorkflowRuntime(
        divergent,
        stateRepository,
        mock(StepSequenceExecutor.class),
        eventRecorder,
        CLOCK,
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        null,
        null,
        new DefaultRequirementResolver(),
        new TransitionGate(eventRecorder),
        RunExecutionInterceptor.NO_OP,
        new InMemoryGeneratedArtifactStore());

    assertThatThrownBy(() -> runtime.start("wf-divergent"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("collect1")
        .hasMessageContaining("CollectionBehaviour");

    assertThat(stateRepository.findAll()).isEmpty();
    assertThat(eventLog.appended()).isEmpty();
  }

  @Test
  void start_stillExecutesAnOrdinaryRedefinitionThatArrivesAfterBuildWithNoCollectionStep() {
    WorkflowDefinition original = workflow("wf-dynamic-ordinary", Map.of(),
        List.of(resourceStep("s1", "s1.out")));
    InMemoryWorkflowRepository repository =
        new InMemoryWorkflowRepository(Map.of(original.id(), original));
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(repository)
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .agentInvoker(unusedAgentInvoker())
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    // A legitimate redefinition (still no COLLECTION step) replaces the original between build()
    // and start() — this must execute normally, not be caught up in the COLLECTION gate.
    WorkflowDefinition redefined = workflow("wf-dynamic-ordinary", Map.of(),
        List.of(resourceStep("s1", "s1.out"), resourceStep("s2", "s2.out")));
    repository.replace(Map.of(redefined.id(), redefined));

    String runId = runtime.start(redefined.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(runtime.getState(runId).getContext()).containsKey("s2.out");
  }

  private static StepDefinition assignChoiceStep() {
    return StepDefinition.builder()
        .withStepId("assign-choice")
        .withName("assign-choice")
        .withBehaviour(new AssignContextBehaviour(
            "choice", new StringContextValue("go", ContextProvenance.SYSTEM_GENERATED)))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static StepDefinition resourceStep(String stepId, String contextKey) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", contextKey, StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static StepDefinition collectionStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new CollectionBehaviour(
            null, 0, null, null, 0, null, null, null, null, null, null, null, StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static WorkflowDefinition workflow(String id, Map<String, BlueprintDefinition> blueprints,
      List<Executable> steps) {
    return new WorkflowDefinition(id, id, null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, steps, List.of());
  }

  private static WorkflowRuntimeBuilder builderFor(WorkflowDefinition workflow) {
    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .agentInvoker(unusedAgentInvoker())
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER);
  }

  private static AgentInvoker unusedAgentInvoker() {
    ObjectMapper mapper = new ObjectMapper();
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    return AgentInvoker.builder()
        .agentRepository(mock(AgentRepository.class))
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

  /**
   * Event log that captures every appended event regardless of run id. The production log is keyed
   * by runId — which {@code start()} mints internally and never exposes on the rejection path — so
   * proving "no event was recorded" requires capturing all appends, not querying by a guessed key.
   */
  private static final class CapturingEventLog implements WorkflowEventLog {

    private final List<WorkflowEvent> appended = new ArrayList<>();

    @Override
    public void append(WorkflowEvent event) {
      appended.add(event);
    }

    @Override
    public List<WorkflowEvent> getEvents(String runId) {
      return appended.stream().filter(event -> runId.equals(event.runId())).toList();
    }

    List<WorkflowEvent> appended() {
      return List.copyOf(appended);
    }
  }
}
