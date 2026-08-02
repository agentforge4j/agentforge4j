// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
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
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end guard that the built-in {@code agent-bundle} validator is wired into a default-assembled runtime through
 * {@link java.util.ServiceLoader} discovery — with no embedder-supplied validators. A {@code VALIDATE} step selecting
 * {@code agent-bundle} over a valid generated bundle completes; were the validator not discovered the same step would
 * fail closed with {@code no ArtifactValidator registered for validatorId 'agent-bundle'}.
 */
class BuiltInArtifactValidatorDiscoveryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String AGENT_JSON =
      "{\"id\":\"a\",\"name\":\"A\",\"locality\":\"CLOUD\","
          + "\"providerPreferences\":[{\"provider\":\"openai\",\"model\":null}],\"version\":\"1.0.0\"}";

  @Test
  void builtInAgentBundleValidatorIsDiscoveredAndResolvesWithoutEmbedderRegistration() {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withLlmClientResolver(resolver())
        .withAgentRepository(agentRepository())
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", workflow())))
        .withWorkflowStateRepository(stateRepository)
        .withFileSink(FileSink.NO_OP_FILE_SINK)
        .build();

    String runId = af.runtime().start("wf1");

    WorkflowState state = stateRepository.findById(runId).orElseThrow();
    assertThat(state.getStatus())
        .as("the discovered built-in agent-bundle validator must resolve and pass a valid bundle")
        .isEqualTo(WorkflowStatus.COMPLETED);
  }

  private static LlmClientResolver resolver() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(bundleScript(), null, null));

    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    return resolver;
  }

  private static AgentRepository agentRepository() {
    AgentDefinition agentDef = AgentDefinition.builder()
        .withId("a1")
        .withName("A")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("CREATE_FILE", "COMPLETE"))
        .withVersion("1.0.0")
        .build();
    AgentRepository agentRepository = mock(AgentRepository.class);
    when(agentRepository.get("a1")).thenReturn(agentDef);
    return agentRepository;
  }

  private static WorkflowDefinition workflow() {
    StepDefinition generate = StepDefinition.builder()
        .withStepId("generate")
        .withName("Generate")
        .withBehaviour(new AgentBehaviour("a1", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
    StepDefinition validate = StepDefinition.builder()
        .withStepId("validate")
        .withName("Validate")
        .withBehaviour(new ValidateBehaviour(
            "agent-bundle", List.of("agent.json", "systemprompt.md"), List.of()))
        .withContextMapping(ContextMapping.none())
        .build();
    return new WorkflowDefinition(
        "wf1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(generate, validate), List.of());
  }

  private static String bundleScript() {
    try {
      return MAPPER.writeValueAsString(List.of(
          Map.of("type", "CREATE_FILE", "path", "agent.json", "content", AGENT_JSON),
          Map.of("type", "CREATE_FILE", "path", "systemprompt.md", "content", "You are an agent."),
          Map.of("type", "COMPLETE")));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
