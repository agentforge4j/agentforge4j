// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
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

/**
 * Integration-style drive over the real {@link WorkflowRuntimeBuilder} graph: no Mockito, no HTTP,
 * only in-memory repositories and a trivial in-process LLM stub.
 */
class WorkflowRuntimeDriveIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void wiredRuntime_completesSingleAgentStep() {
    AgentDefinition agent = AgentDefinition.builder()
        .withId("agent1")
        .withName("Agent")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("stub system")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-test")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();

    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("Step1")
        .withBehaviour(new AgentBehaviour(agent.id(), StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .withMaxUserPromptRounds(8)
        .build();

    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-it",
        "IT",
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(step));

    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    AgentInvoker agentInvoker = generateAgentInvoker(eventLog, clock,
        Map.of(agent.id(), agent));

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  private static AgentInvoker generateAgentInvoker(InMemoryWorkflowEventLog eventLog, Clock clock,
      Map<String, AgentDefinition> agent) {
    EventRecorder eventRecorder = new EventRecorder(eventLog, clock);
    LlmClientResolver resolver =
        new SingleClientResolver(new ConstantJsonLlmClient("[{\"type\":\"COMPLETE\"}]"));
    return AgentInvoker.builder()
        .agentRepository(new MapAgentRepository(agent))
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(MAPPER))
        .llmCommandParser(new LlmCommandParser(MAPPER))
        .objectMapper(MAPPER)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, MAPPER))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  @Test
  void nested_workflow_blueprint_shadows_root_same_id_blueprint() {
    StepDefinition stepA = resourceStep("step-a", "/examples/sample.txt", "root.executed");
    StepDefinition stepB = resourceStep("step-b", "/workflow-resources/info.txt",
        "nested.executed");
    BlueprintDefinition rootBp = blueprint("bp1", List.of(stepA));
    BlueprintDefinition nestedBp = blueprint("bp1", List.of(stepB));

    WorkflowDefinition nested = workflow(
        "wf-nested",
        Map.of("bp1", nestedBp),
        Map.of(),
        List.of(new BlueprintRef("bp1")));

    WorkflowDefinition root = workflow(
        "wf-root",
        Map.of("bp1", rootBp),
        Map.of(),
        List.of(nestedWorkflowStep("wf-nested")));

    WorkflowRuntime runtime = runtime(Map.of(root.id(), root, nested.id(), nested));
    String runId = runtime.start(root.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(runtime.getState(runId).getContext()).containsKey("nested.executed");
    assertThat(runtime.getState(runId).getContext()).doesNotContainKey("root.executed");
  }

  @Test
  void nested_workflow_input_artifact_resolves_from_nested_map() {
    ArtifactDefinition rootArtifact = new ArtifactDefinition(
        "form1", List.of(new TextArtifactItem("rootField", "Root", true, null)));
    ArtifactDefinition nestedArtifact = new ArtifactDefinition(
        "form1", List.of(new TextArtifactItem("nestedField", "Nested", true, null)));

    StepDefinition inputStep = StepDefinition.builder()
        .withStepId("input")
        .withName("input")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();

    WorkflowDefinition nested = workflow(
        "wf-nested-artifact",
        Map.of(),
        Map.of("form1", nestedArtifact),
        List.of(inputStep));

    WorkflowDefinition root = workflow(
        "wf-root-artifact",
        Map.of(),
        Map.of("form1", rootArtifact),
        List.of(nestedWorkflowStep("wf-nested-artifact")));

    WorkflowRuntime runtime = runtime(Map.of(root.id(), root, nested.id(), nested));
    String runId = runtime.start(root.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(runtime.getState(runId).getPendingArtifact()).isNotNull();
    assertThat(runtime.getState(runId).getPendingArtifact().items().get(0).id()).isEqualTo(
        "nestedField");
  }

  private static WorkflowRuntime runtime(Map<String, WorkflowDefinition> workflows) {
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    AgentInvoker agentInvoker = generateAgentInvoker(eventLog, clock,
        Map.of());

    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(workflows))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();
  }

  private static StepDefinition resourceStep(String stepId, String resourcePath,
      String contextKey) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour(resourcePath, contextKey, StepTransition.AUTO))
        .withContextMapping(new ContextMapping(List.of(), List.of()))
        .build();
  }

  private static StepDefinition nestedWorkflowStep(String workflowRef) {
    return StepDefinition.builder()
        .withStepId("invoke-nested")
        .withName("invoke-nested")
        .withBehaviour(new WorkflowBehaviour(workflowRef, StepTransition.AUTO))
        .build();
  }

  private static BlueprintDefinition blueprint(String id,
      List<com.agentforge4j.core.workflow.Executable> steps) {
    return new BlueprintDefinition(
        id,
        id,
        new BlueprintBehaviour(null, StepTransition.AUTO),
        steps);
  }

  private static WorkflowDefinition workflow(String id,
      Map<String, BlueprintDefinition> blueprints,
      Map<String, ArtifactDefinition> artifacts,
      List<com.agentforge4j.core.workflow.Executable> steps) {
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
        artifacts,
        blueprints,
        steps);
  }

  private static final class MapAgentRepository implements AgentRepository {

    private final Map<String, AgentDefinition> agents;

    MapAgentRepository(Map<String, AgentDefinition> agents) {
      this.agents = Map.copyOf(agents);
    }

    @Override
    public AgentDefinition get(String id) {
      AgentDefinition def = agents.get(id);
      if (def == null) {
        throw new IllegalArgumentException("No agent: " + id);
      }
      return def;
    }

    @Override
    public Map<String, AgentDefinition> findAll() {
      return agents;
    }
  }

  private static final class SingleClientResolver implements LlmClientResolver {

    private final LlmClient client;

    SingleClientResolver(LlmClient client) {
      this.client = client;
    }

    @Override
    public LlmClient resolve(String provider) {
      return client;
    }

    @Override
    public boolean isProviderAvailable(String provider) {
      return true;
    }

    @Override
    public List<String> listAvailableClients() {
      return List.of(client.getProviderName());
    }
  }

  private static final class ConstantJsonLlmClient implements LlmClient {

    private final String jsonBody;

    ConstantJsonLlmClient(String jsonBody) {
      this.jsonBody = jsonBody;
    }

    @Override
    public String getProviderName() {
      return "openai";
    }

    @Override
    public LlmExecutionResponse execute(LlmExecutionRequest request) {
      return new LlmExecutionResponse(jsonBody, null, null);
    }
  }
}
