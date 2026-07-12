// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.agent.RequiredArtifactsPresentValidator;
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
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
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
 * End-to-end: an agent step emits generated artifacts via {@code CREATE_FILE}; the following {@code VALIDATE} step
 * runs the {@code required-artifacts-present} validator. Passes to completion when every declared path is captured,
 * and fails the run closed (with audit) when one of them is missing. LLM is mocked.
 */
class RequiredArtifactsPresentValidatorRuntimeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void run_completes_when_every_required_artifact_is_captured() throws Exception {
    Fixture f = fixture(generationResponse(true));

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getGeneratedArtifactDescriptors())
        .extracting(d -> d.path())
        .containsExactlyInAnyOrder("notes.txt", "summary.txt");
  }

  @Test
  void run_fails_closed_when_a_required_artifact_is_missing() throws Exception {
    Fixture f = fixture(generationResponse(false));

    String runId = f.runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getFailureReason()).contains("required artifacts not captured", "summary.txt");
  }

  private static String generationResponse(boolean emitBothArtifacts) throws Exception {
    List<Map<String, Object>> commands = emitBothArtifacts
        ? List.of(
            Map.of("type", "CREATE_FILE", "path", "notes.txt", "content", "some notes"),
            Map.of("type", "CREATE_FILE", "path", "summary.txt", "content", "a summary"),
            Map.of("type", "COMPLETE"))
        : List.of(
            Map.of("type", "CREATE_FILE", "path", "notes.txt", "content", "some notes"),
            Map.of("type", "COMPLETE"));
    return MAPPER.writeValueAsString(commands);
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
        .withSupportedCommands(List.of("CREATE_FILE", "COMPLETE"))
        .withVersion("1.0.0")
        .build();
    AgentRepository agentRepository = mock(AgentRepository.class);
    when(agentRepository.get("creator")).thenReturn(agentDef);

    StepDefinition generate = StepDefinition.builder()
        .withStepId("gen")
        .withName("Generate")
        .withBehaviour(new AgentBehaviour("creator", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
    StepDefinition validate = StepDefinition.builder()
        .withStepId("validate")
        .withName("Validate")
        .withBehaviour(new ValidateBehaviour(
            RequiredArtifactsPresentValidator.VALIDATOR_ID,
            List.of("notes.txt", "summary.txt"),
            List.of()))
        .withContextMapping(ContextMapping.none())
        .build();
    WorkflowDefinition wf = WorkflowDefinition.builder()
        .withId("wf1")
        .withName("W")
        .withSource(WorkflowSource.CUSTOM)
        .withLifecycle(WorkflowLifecycle.ACTIVE)
        .withSteps(List.of(generate, validate))
        .build();

    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
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
        .artifactValidators(List.of(new RequiredArtifactsPresentValidator()))
        .build();

    return new Fixture(runtime, stateRepository);
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowStateRepository stateRepository) {
  }
}
