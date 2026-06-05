package com.agentforge4j.runtime.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.ModelTierResolutionException;
import com.agentforge4j.llm.api.ModelTierResolver;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentInvokerModelTierTest {

  private static final String COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  @Test
  void pinWinsOverTierAndResolverIsNotConsulted() {
    LlmClient client = client();
    ModelTierResolver resolver = mock(ModelTierResolver.class);
    AgentInvoker invoker = invoker(agent(new ProviderPreference("openai", "pinned-model"),
        "POWERFUL"), client, resolver);

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(),
        state("run-pin"), null);

    assertThat(requestModel(client)).isEqualTo("pinned-model");
    assertThat(result.modelSource()).isEqualTo(ModelSource.PIN);
    assertThat(result.resolvedModel()).isEqualTo("pinned-model");
    assertThat(result.requestedModelTier()).isNull();
    verify(resolver, never()).resolve(any(), any());
  }

  @Test
  void agentTierIsResolvedWhenNoPin() {
    LlmClient client = client();
    ModelTierResolver resolver = (provider, tier) ->
        tier == ModelTier.STANDARD ? "resolved-standard" : null;
    AgentInvoker invoker = invoker(agent(new ProviderPreference("openai", null), "STANDARD"),
        client, resolver);

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(),
        state("run-agent-tier"), null);

    assertThat(requestModel(client)).isEqualTo("resolved-standard");
    assertThat(result.modelSource()).isEqualTo(ModelSource.TIER);
    assertThat(result.resolvedModel()).isEqualTo("resolved-standard");
    assertThat(result.requestedModelTier()).isEqualTo(ModelTier.STANDARD);
  }

  @Test
  void stepTierOverridesAgentTier() {
    LlmClient client = client();
    ModelTierResolver resolver = (provider, tier) -> "resolved-" + tier.name();
    AgentInvoker invoker = invoker(agent(new ProviderPreference("openai", null), "LITE"),
        client, resolver);

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(),
        state("run-step-tier"), null, "POWERFUL");

    assertThat(requestModel(client)).isEqualTo("resolved-POWERFUL");
    assertThat(result.requestedModelTier()).isEqualTo(ModelTier.POWERFUL);
  }

  @Test
  void unresolvedTierThrows() {
    LlmClient client = client();
    ModelTierResolver resolver = (provider, tier) -> null;
    AgentInvoker invoker = invoker(agent(new ProviderPreference("openai", null), "STANDARD"),
        client, resolver);

    assertThatThrownBy(() ->
        invoker.invoke("agent-x", ContextMapping.none(), state("run-miss"), null))
        .isInstanceOf(ModelTierResolutionException.class)
        .hasMessageContaining("openai")
        .hasMessageContaining("STANDARD");
  }

  @Test
  void invalidTierNameThrows() {
    LlmClient client = client();
    ModelTierResolver resolver = (provider, tier) -> "x";
    AgentInvoker invoker = invoker(agent(new ProviderPreference("openai", null), "SUPER"),
        client, resolver);

    assertThatThrownBy(() ->
        invoker.invoke("agent-x", ContextMapping.none(), state("run-bad-tier"), null))
        .isInstanceOf(ModelTierResolutionException.class)
        .hasMessageContaining("SUPER");
  }

  @Test
  void untieredUnpinnedUsesProviderDefault() {
    LlmClient client = client();
    ModelTierResolver resolver = mock(ModelTierResolver.class);
    AgentInvoker invoker = invoker(agent(new ProviderPreference("openai", null), null),
        client, resolver);

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(),
        state("run-default"), null);

    assertThat(requestModel(client)).isNull();
    assertThat(result.modelSource()).isEqualTo(ModelSource.PROVIDER_DEFAULT);
    assertThat(result.resolvedModel()).isNull();
    assertThat(result.requestedModelTier()).isNull();
    verify(resolver, never()).resolve(any(), any());
  }

  private static String requestModel(LlmClient client) {
    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    return captor.getValue().model();
  }

  private static LlmClient client() {
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(new LlmExecutionResponse(COMPLETE, "openai-model", null));
    return client;
  }

  private static AgentInvoker invoker(AgentDefinition agent, LlmClient client,
      ModelTierResolver resolver) {
    ObjectMapper mapper = new ObjectMapper();
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agent);
    LlmClientResolver clientResolver = mock(LlmClientResolver.class);
    when(clientResolver.resolve("openai")).thenReturn(client);
    when(clientResolver.isProviderAvailable("openai")).thenReturn(true);
    when(clientResolver.listAvailableClients()).thenReturn(List.of("openai"));
    EventRecorder recorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC));
    return AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(clientResolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(false)
        .llmCallObserver(new LlmCallObserver(recorder))
        .modelTierResolver(resolver)
        .build();
  }

  private static AgentDefinition agent(ProviderPreference preference, String modelTier) {
    return AgentDefinition.builder()
        .withId("a1")
        .withName("A")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(preference))
        .withSupportedCommands(List.of("COMPLETE"))
        .withAuthor(null)
        .withContact(null)
        .withVersion("1.0.0")
        .withModelTier(modelTier)
        .build();
  }

  private static WorkflowState state(String runId) {
    WorkflowState state = new WorkflowState(runId, "wf-1", null,
        Instant.parse("2026-01-01T00:00:00Z"));
    state.setCurrentStepId("step-1");
    return state;
  }
}
