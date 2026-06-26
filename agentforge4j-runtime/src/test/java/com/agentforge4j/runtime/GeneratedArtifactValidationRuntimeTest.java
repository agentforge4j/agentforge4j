// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.agent.AgentBundleArtifactValidator;
import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ContextEqualityContract;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end: an agent step emits an agent bundle via {@code CREATE_FILE} and sets
 * {@code recommendedTier}; the following {@code VALIDATE} step runs the agent-bundle validator and a
 * {@code /modelTier == recommendedTier} equality contract. Passes to completion when consistent, and
 * fails the run closed (with audit) when the contract is violated. LLM is mocked.
 */
class GeneratedArtifactValidationRuntimeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private static String agentJson(String modelTier) {
    return "{\"id\":\"created\",\"name\":\"Created\",\"locality\":\"CLOUD\",\"modelTier\":\""
        + modelTier
        + "\",\"providerPreferences\":[{\"provider\":\"openai\",\"model\":\"gpt-4o-mini\"}],"
        + "\"version\":\"1.0.0\"}";
  }

  private static String generationResponse(String agentModelTier, String recommendedTier)
      throws Exception {
    List<Map<String, Object>> commands = List.of(
        Map.of("type", "CREATE_FILE", "path", "agent.json", "content", agentJson(agentModelTier)),
        Map.of("type", "CREATE_FILE", "path", "systemprompt.md", "content", "You are created."),
        Map.of("type", "CREATE_FILE", "path", "README.md", "content", "# Created"),
        Map.of("type", "SET_CONTEXT", "key", "recommendedTier",
            "value", Map.of("type", "STRING", "value", recommendedTier)),
        Map.of("type", "COMPLETE"));
    return MAPPER.writeValueAsString(commands);
  }

  @Test
  void agent_bundle_validates_and_run_completes_when_tier_matches() throws Exception {
    Fixture f = fixture(generationResponse("POWERFUL", "POWERFUL"));

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getGeneratedArtifactDescriptors())
        .extracting(d -> d.path())
        .containsExactlyInAnyOrder("agent.json", "systemprompt.md", "README.md");
  }

  @Test
  void run_fails_closed_when_model_tier_contract_violated() throws Exception {
    Fixture f = fixture(generationResponse("LITE", "POWERFUL"));

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    String stepFailed = f.eventLog().getEvents(runId).stream()
        .filter(e -> e.eventType() == WorkflowEventType.STEP_FAILED)
        .map(WorkflowEvent::payload)
        .reduce("", (a, b) -> a + b);
    assertThat(stepFailed).contains("equality contract violated");
  }

  private static Fixture fixture(String generationResponse) {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(generationResponse, null, null));

    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentDefinition agentDef = AgentDefinition.builder()
        .withId("creator")
        .withName("Creator")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("CREATE_FILE", "SET_CONTEXT", "COMPLETE"))
        .withVersion("1.0.0")
        .build();
    AgentRepository agentRepository = mock(AgentRepository.class);
    when(agentRepository.get("creator")).thenReturn(agentDef);

    StepDefinition generate = StepDefinition.builder()
        .withStepId("gen")
        .withName("Generate")
        .withBehaviour(new AgentBehaviour("creator", StepTransition.AUTO, null))
        .withContextMapping(new ContextMapping(List.of(), List.of("recommendedTier")))
        .build();
    StepDefinition validate = StepDefinition.builder()
        .withStepId("validate")
        .withName("Validate")
        .withBehaviour(new ValidateBehaviour(
            AgentBundleArtifactValidator.VALIDATOR_ID,
            List.of("agent.json", "systemprompt.md", "README.md"),
            List.of(new ContextEqualityContract("agent.json", "/modelTier", "recommendedTier"))))
        .withContextMapping(new ContextMapping(List.of("recommendedTier"), List.of()))
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(generate, validate));

    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);
    AgentInvoker agentInvoker = AgentInvoker.builder()
        .agentRepository(agentRepository)
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

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", wf)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(CLOCK)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .artifactValidators(List.of(new AgentBundleArtifactValidator()))
        .build();

    return new Fixture(runtime, eventLog, stateRepository);
  }

  private record Fixture(
      WorkflowRuntime runtime,
      WorkflowEventLog eventLog,
      WorkflowStateRepository stateRepository) {
  }
}
