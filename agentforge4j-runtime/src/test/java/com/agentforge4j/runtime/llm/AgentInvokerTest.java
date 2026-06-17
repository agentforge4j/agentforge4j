// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandResponseSchemaRenderer;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.command.schema.SystemRulesProvider;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationIdentity;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentInvokerTest {

  @Test
  void invoke_populates_tokenUsage_on_result() {
    ObjectMapper mapper = new ObjectMapper();
    String raw = "[{\"type\":\"COMPLETE\"}]";
    TokenUsageReport usage = new TokenUsageReport(10, 5, null, null);
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw, "gpt-4o-mini", usage));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-token-usage");

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(), state, null);

    assertThat(result.tokenUsage()).isEqualTo(usage);
  }

  @Test
  void invoke_withoutActiveWorkflowId_populatesIdentityFromRootStateAndAgent() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-identity");

    // Convenience overload without an execution context: identity workflowId falls back to the
    // run's root workflow id (state.getWorkflowId()).
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    LlmInvocationIdentity identity = captor.getValue().identity();
    assertThat(identity).isNotNull();
    assertThat(identity.workflowId()).isEqualTo("wf-1");
    assertThat(identity.runId()).isEqualTo("run-identity");
    assertThat(identity.stepId()).isEqualTo("step-1");
    assertThat(identity.agentId()).isEqualTo("a1");
  }

  @Test
  void invoke_withActiveWorkflowId_usesItAsIdentityWorkflowId() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-nested");

    // Nested workflow: the active workflow id differs from the run's root workflow id.
    invoker.invoke("agent-x", ContextMapping.none(), state, null, null, "epic-implementation");

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    LlmInvocationIdentity identity = captor.getValue().identity();
    assertThat(identity.workflowId()).isEqualTo("epic-implementation");
    assertThat(identity.workflowId()).isNotEqualTo(state.getWorkflowId());
    assertThat(identity.runId()).isEqualTo("run-nested");
    assertThat(identity.stepId()).isEqualTo("step-1");
    assertThat(identity.agentId()).isEqualTo("a1");
  }

  @Test
  void distinctActiveWorkflowIds_yieldDistinctIdentities_forSameRunStepAndAgent() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-collide");

    // Two different nested sub-workflows under one run reuse the same stepId + agentId.
    invoker.invoke("agent-x", ContextMapping.none(), state, null, null, "sub-a");
    invoker.invoke("agent-x", ContextMapping.none(), state, null, null, "sub-b");

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    LlmInvocationIdentity a = captor.getAllValues().get(0).identity();
    LlmInvocationIdentity b = captor.getAllValues().get(1).identity();
    assertThat(a.runId()).isEqualTo(b.runId());
    assertThat(a.stepId()).isEqualTo(b.stepId());
    assertThat(a.agentId()).isEqualTo(b.agentId());
    // Active workflow id is the only differentiator — keys do not collide.
    assertThat(a.workflowId()).isEqualTo("sub-a");
    assertThat(b.workflowId()).isEqualTo("sub-b");
    assertThat(a.workflowId()).isNotEqualTo(b.workflowId());
  }

  @Test
  void invoke_populates_modelUsed_on_result() {
    ObjectMapper mapper = new ObjectMapper();
    String raw = "[{\"type\":\"COMPLETE\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw, "claude-3-5-sonnet", null));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-model-used");

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(), state, null);

    assertThat(result.modelUsed()).isEqualTo("claude-3-5-sonnet");
  }

  @Test
  void invoke_with_null_tokenUsage_propagates_null() {
    ObjectMapper mapper = new ObjectMapper();
    String raw = "[{\"type\":\"COMPLETE\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw, "gpt-4o", null));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-null-usage");

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(), state, null);

    assertThat(result.tokenUsage()).isNull();
  }

  @Test
  void parseRetryPipeline_successfulFirstParse_oneLlmCall_oneAudit_noDuplicates() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String runId = "run-first-ok";
    String raw = "[{\"type\":\"COMPLETE\"}]";

    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw));

    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder);
    WorkflowState state = workflowState(runId);

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(), state, null);

    assertThat(result.commands()).hasSize(1);
    assertThat(result.commands().get(0)).isInstanceOf(CompleteCommand.class);
    assertThat(result.rawResponse()).isEqualTo(raw);

    int expectedLlmCalls = 1;
    verify(client, times(expectedLlmCalls)).execute(any());
    assertThat(llmOutputEventCount(eventLog, runId)).isEqualTo(expectedLlmCalls);
  }

  @Test
  void parseRetryPipeline_failedFirstParseSuccessfulRetry_twoLlmCalls_twoAudits_noDuplicates() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String runId = "run-retry-ok";
    String firstRaw = "{}";
    String secondRaw = "[{\"type\":\"COMPLETE\"}]";

    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(firstRaw), llmResponse(secondRaw));

    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder);
    WorkflowState state = workflowState(runId);

    AgentInvocationResult result = invoker.invoke("agent-x", ContextMapping.none(), state, null);

    assertThat(result.rawResponse()).isEqualTo(secondRaw);
    assertThat(result.commands()).hasSize(1);
    assertThat(result.commands().get(0)).isInstanceOf(CompleteCommand.class);

    int expectedLlmCalls = 2;
    verify(client, times(expectedLlmCalls)).execute(any());
    assertThat(llmOutputEventCount(eventLog, runId)).isEqualTo(expectedLlmCalls);

    List<com.agentforge4j.core.workflow.event.WorkflowEvent> llmOutputs = eventLog.getEvents(runId)
        .stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .toList();
    assertThat(llmOutputs.get(0).payload()).isEqualTo(firstRaw);
    assertThat(llmOutputs.get(1).payload()).isEqualTo(secondRaw);
  }

  @Test
  void parseRetryPipeline_failedBothParses_throwsFinalParseException_twoCalls_twoAudits_noDuplicates() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String runId = "run-both-fail";
    String bad = """
        [{"type":"CREATE_FILE","path":"p","content":"c"}]
        """;

    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(bad.strip()), llmResponse(bad.strip()));

    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder);
    WorkflowState state = workflowState(runId);

    assertThatThrownBy(() -> invoker.invoke("agent-x", ContextMapping.none(), state, null))
        .isInstanceOf(LlmCommandParseException.class)
        .hasMessageContaining("CREATE_FILE")
        .hasMessageContaining("not enabled for this agent");

    int expectedLlmCalls = 2;
    verify(client, times(expectedLlmCalls)).execute(any());
    assertThat(llmOutputEventCount(eventLog, runId)).isEqualTo(expectedLlmCalls);
  }

  @Test
  void parseRetryPipeline_secondLlmRequestUserInputContainsCorrectionFromFirstParseFailure() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String runId = "run-correction";
    String userInput = "{\"ctx\":true}";

    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("{}"),
        llmResponse("[{\"type\":\"COMPLETE\"}]"));

    ContextRenderer contextRenderer = mock(ContextRenderer.class);
    when(contextRenderer.render(any(), any())).thenReturn(userInput);

    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder, contextRenderer);
    WorkflowState state = workflowState(runId);

    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    int expectedLlmCalls = 2;
    verify(client, times(expectedLlmCalls)).execute(captor.capture());
    assertThat(captor.getAllValues()).hasSize(expectedLlmCalls);
    assertThat(llmOutputEventCount(eventLog, runId)).isEqualTo(expectedLlmCalls);

    List<LlmExecutionRequest> requests = captor.getAllValues();
    assertThat(requests.get(0).userInput()).isEqualTo(userInput);
    String expectedCorrectionLead = userInput
        + System.lineSeparator()
        + System.lineSeparator()
        + "CORRECTION REQUIRED (your prior reply broke the command contract): ";
    assertThat(requests.get(1).userInput()).startsWith(expectedCorrectionLead);
  }

  @Test
  void retryCorrectionComposesOutsideTheUntrustedInputEnvelope() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);

    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("{}"), llmResponse("[{\"type\":\"COMPLETE\"}]"));

    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyComplete());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    // Real renderer: an untrusted context entry lands inside the envelope, which forms the userInput.
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    WorkflowState state = workflowState("run-correction-envelope");
    state.putContextValue("k", new StringContextValue("v", ContextProvenance.USER_SUPPLIED));

    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    String envelope = "{\"%s\":{\"k\":\"v\"}}".formatted(ContextRenderer.UNTRUSTED_USER_INPUT_KEY);
    // First attempt: userInput is exactly the envelope.
    assertThat(captor.getAllValues().get(0).userInput()).isEqualTo(envelope);
    // Retry: the correction text is appended AFTER the envelope JSON, outside it.
    String secondInput = captor.getAllValues().get(1).userInput();
    assertThat(secondInput).startsWith(envelope
        + System.lineSeparator() + System.lineSeparator()
        + "CORRECTION REQUIRED (your prior reply broke the command contract): ");
  }

  @Test
  void parseRetryPipeline_auditEventCountEqualsLlmCallCountAcrossScenarios() {
    ObjectMapper mapper = new ObjectMapper();
    String bad = """
        [{"type":"CREATE_FILE","path":"p","content":"c"}]
        """;

    assertScenarioAuditMatchesLlmCalls(
        mapper, "run-a", "[{\"type\":\"COMPLETE\"}]", 1);
    assertScenarioAuditMatchesLlmCalls(
        mapper, "run-b", "{}", "[{\"type\":\"COMPLETE\"}]", 2);
    assertScenarioAuditMatchesLlmCalls(
        mapper, "run-c", bad.strip(), bad.strip(), 2, true);
  }

  @Test
  void llm_output_under_cap_is_recorded_verbatim() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String raw = "[{\"type\":\"COMPLETE\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw));

    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder);
    WorkflowState state = workflowState("run-cap-short");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    String payload = eventLog.getEvents("run-cap-short").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).isEqualTo(raw);
  }

  @Test
  void llm_output_over_cap_is_truncated_with_marker() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String padding = "x".repeat(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP + 100);
    String raw = "[{\"type\":\"COMPLETE\",\"summary\":\"" + padding + "\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw));

    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder);
    WorkflowState state = workflowState("run-cap-long");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    String payload = eventLog.getEvents("run-cap-long").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).hasSize(
        AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP
            + "... [event payload truncated for audit; original length=".length()
            + String.valueOf(raw.length()).length()
            + " chars]".length());
    assertThat(payload).startsWith(
        raw.substring(0, AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP));
    assertThat(payload).endsWith(
        "... [event payload truncated for audit; original length=" + raw.length() + " chars]");
  }

  @Test
  void cap_of_zero_disables_truncation() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String padding = "y".repeat(20_000);
    String raw = "[{\"type\":\"COMPLETE\",\"summary\":\"" + padding + "\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw));

    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyComplete());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmOutputEventCharCap(0)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    WorkflowState state = workflowState("run-cap-zero");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    String payload = eventLog.getEvents("run-cap-zero").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).isEqualTo(raw);
  }

  @Test
  void custom_cap_is_honoured() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String padding = "z".repeat(200);
    String raw = "[{\"type\":\"COMPLETE\",\"summary\":\"" + padding + "\"}]";
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse(raw));

    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyComplete());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmOutputEventCharCap(100)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    WorkflowState state = workflowState("run-cap-custom");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    String payload = eventLog.getEvents("run-cap-custom").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).hasSize(
        100 + "... [event payload truncated for audit; original length=".length()
            + String.valueOf(raw.length()).length() + " chars]".length());
    assertThat(payload).startsWith(raw.substring(0, 100));
    assertThat(payload).endsWith(
        "... [event payload truncated for audit; original length=" + raw.length() + " chars]");
  }

  @Test
  void builder_rejects_negative_cap() {
    ObjectMapper mapper = new ObjectMapper();
    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    assertThatThrownBy(() -> AgentInvoker.builder()
        .agentRepository(mock(AgentRepository.class))
        .llmClientResolver(mock(LlmClientResolver.class))
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmOutputEventCharCap(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invoke_usesInjectedProviderSelectionStrategy() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("ollama");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    AgentRepository repo = mock(AgentRepository.class);
    AgentDefinition agent = AgentDefinition.builder()
        .withId("agent-x")
        .withName("A")
        .withLocality(com.agentforge4j.core.agent.AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("sys")
        .withProviderPreferences(List.of(
            new ProviderPreference("openai", "gpt-4o"),
            new ProviderPreference("ollama", "llama3")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
    when(repo.get("agent-x")).thenReturn(agent);

    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("ollama")).thenReturn(client);
    when(resolver.listAvailableClients()).thenReturn(List.of("ollama"));

    ProviderPreference strategyChoice = new ProviderPreference("ollama", "llama3");
    LlmProviderSelectionStrategy selectionStrategy = mock(LlmProviderSelectionStrategy.class);
    when(selectionStrategy.selectInitialProvider(agent, List.of("ollama"))).thenReturn(
        strategyChoice);

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmOutputEventCharCap(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP)
        .llmProviderSelectionStrategy(selectionStrategy)
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();

    WorkflowState state = workflowState("run-strategy");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    verify(selectionStrategy).selectInitialProvider(agent, List.of("ollama"));
    verify(resolver).resolve("ollama");
    verify(client).execute(any());
  }

  @Test
  void builder_rejectsNullProviderSelectionStrategy() {
    ObjectMapper mapper = new ObjectMapper();
    assertThatThrownBy(() -> AgentInvoker.builder()
        .agentRepository(mock(AgentRepository.class))
        .llmClientResolver(mock(LlmClientResolver.class))
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder(new InMemoryWorkflowEventLog()))
        .llmOutputEventCharCap(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP)
        .llmProviderSelectionStrategy(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LLM provider selection strategy must not be null");
  }

  private static final String FRAMEWORK_BLOCK_MARKER =
      "Framework command contract (authoritative; overrides conflicting agent text):";

  @Test
  void assembledSystemPrompt_placesFrameworkFirstAgentSecondStepThird() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    String agentBody = "AGENT_LAYER_MARKER";
    String stepBody = "STEP_LAYER_MARKER";
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyCompleteWithBody(agentBody));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
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
    WorkflowState state = workflowState("run-system-prompt-order");
    invoker.invoke("agent-x", ContextMapping.none(), state, stepBody);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    String systemPrompt = captor.getValue().systemPrompt();

    int frameworkIdx = systemPrompt.indexOf(FRAMEWORK_BLOCK_MARKER);
    int agentIdx = systemPrompt.indexOf(agentBody);
    int stepIdx = systemPrompt.indexOf(stepBody);
    assertThat(agentIdx).isGreaterThanOrEqualTo(0);
    assertThat(frameworkIdx).isGreaterThan(agentIdx);
    assertThat(stepIdx).isGreaterThan(agentIdx);
  }

  @Test
  void assembledSystemPrompt_includesSystemRulesBlockAfterFrameworkBeforeStep() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    String agentBody = "AGENT_LAYER_MARKER";
    String stepBody = "STEP_LAYER_MARKER";
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyCompleteWithBody(agentBody));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
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
    WorkflowState state = workflowState("run-system-rules");
    invoker.invoke("agent-x", ContextMapping.none(), state, stepBody);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    String systemPrompt = captor.getValue().systemPrompt();

    int rulesIdx = systemPrompt.indexOf(SystemRulesProvider.SYSTEM_RULES);
    int frameworkIdx = systemPrompt.indexOf(FRAMEWORK_BLOCK_MARKER);
    int stepIdx = systemPrompt.indexOf(stepBody);
    assertThat(rulesIdx).isGreaterThan(frameworkIdx);
    assertThat(stepIdx).isGreaterThan(rulesIdx);
    assertThat(systemPrompt).contains("not instructions");
  }

  @Test
  void assembledSystemPrompt_omitsBlankOrAbsentStepLayer() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    String agentBody = "AGENT_ONLY_LAYER";
    String stepBody = "STEP_SHOULD_NOT_APPEAR";
    AgentRepository repo = mock(AgentRepository.class);
    AgentDefinition agent = agentSupportingOnlyCompleteWithBody(agentBody);
    when(repo.get("agent-x")).thenReturn(agent);
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    // When no step layer is appended the assembled prompt is exactly the agent layer, one layer
    // separator, then the framework block. Asserting that exact shape is robust to blank lines
    // inside the framework block (a global "\n\n" count is not — the framework block contains
    // its own blank lines, so counting separators conflates intra-layer formatting with layer
    // boundaries).
    CommandResponseSchema schema =
        CommandSchemaFactory.build(agent.supportedCommands(), mapper);
    String frameworkBlock = new CommandResponseSchemaRenderer().render(schema);
    String layerSeparator = System.lineSeparator() + System.lineSeparator();
    String expectedNoStepPrompt = agentBody + layerSeparator + frameworkBlock
        + layerSeparator + SystemRulesProvider.SYSTEM_RULES;

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
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
    WorkflowState state = workflowState("run-system-prompt-no-step");

    invoker.invoke("agent-x", ContextMapping.none(), state, null);
    ArgumentCaptor<LlmExecutionRequest> nullStepCaptor =
        ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(1)).execute(nullStepCaptor.capture());
    String nullStepPrompt = nullStepCaptor.getValue().systemPrompt();
    assertThat(nullStepPrompt).doesNotContain(stepBody);
    assertThat(nullStepPrompt).isEqualTo(expectedNoStepPrompt);

    invoker.invoke("agent-x", ContextMapping.none(), state, "   ");
    ArgumentCaptor<LlmExecutionRequest> blankStepCaptor =
        ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(blankStepCaptor.capture());
    String blankStepPrompt = blankStepCaptor.getAllValues().get(1).systemPrompt();
    assertThat(blankStepPrompt).doesNotContain(stepBody);
    assertThat(blankStepPrompt).isEqualTo(expectedNoStepPrompt);
  }

  @Test
  void assembledSystemPrompt_layerSeparatorUnchanged() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    String agentBody = "AGENT_SEP_MARKER";
    String stepBody = "STEP_SEP_MARKER";
    AgentRepository repo = mock(AgentRepository.class);
    AgentDefinition agent = agentSupportingOnlyCompleteWithBody(agentBody);
    when(repo.get("agent-x")).thenReturn(agent);
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    CommandResponseSchema schema =
        CommandSchemaFactory.build(agent.supportedCommands(), mapper);
    String frameworkBlock = new CommandResponseSchemaRenderer().render(schema);
    String layerSeparator = System.lineSeparator() + System.lineSeparator();
    String expectedWithStep = agentBody
        + layerSeparator
        + frameworkBlock
        + layerSeparator
        + SystemRulesProvider.SYSTEM_RULES
        + layerSeparator
        + stepBody;

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
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
    WorkflowState state = workflowState("run-system-prompt-separator");
    invoker.invoke("agent-x", ContextMapping.none(), state, stepBody);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    assertThat(captor.getValue().systemPrompt()).isEqualTo(expectedWithStep);
  }

  @Test
  void promptLayerBoundaries_areDeterministicForIdenticalInputs() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyCompleteWithBody("agent-body"));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmOutputEventCharCap(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    WorkflowState state = workflowState("run-boundaries-deterministic");
    String stepBody = "STEP_BOUNDARY_MARKER";

    invoker.invoke("agent-x", ContextMapping.none(), state, stepBody);
    invoker.invoke("agent-x", ContextMapping.none(), state, stepBody);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    PromptLayerBoundaries first = captor.getAllValues().get(0).promptLayerBoundaries();
    PromptLayerBoundaries second = captor.getAllValues().get(1).promptLayerBoundaries();
    assertThat(first).isEqualTo(second);
    assertThat(first.layer1EndOffset()).isNotNull();
    assertThat(first.layer2EndOffset()).isNotNull();
    assertThat(first.layer3EndOffset()).isNotNull();
  }

  @Test
  void promptLayerBoundaries_layer1StableForSameSupportedCommands() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-a")).thenReturn(agentSupportingOnlyCompleteWithBody("agent-a-body"));
    when(repo.get("agent-b")).thenReturn(AgentDefinition.builder()
        .withId("agent-b")
        .withName("B")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt("agent-a-body")
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
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
    WorkflowState state = workflowState("run-layer1-stable");

    invoker.invoke("agent-a", ContextMapping.none(), state, null);
    invoker.invoke("agent-b", ContextMapping.none(), state, null);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    byte[] layer1AgentA = layerPrefixBytes(
        captor.getAllValues().get(0).systemPrompt(),
        captor.getAllValues().get(0).promptLayerBoundaries().layer1EndOffset());
    byte[] layer1AgentB = layerPrefixBytes(
        captor.getAllValues().get(1).systemPrompt(),
        captor.getAllValues().get(1).promptLayerBoundaries().layer1EndOffset());
    assertThat(layer1AgentA).isEqualTo(layer1AgentB);
    assertThat(captor.getAllValues().get(0).promptLayerBoundaries().layer1EndOffset())
        .isEqualTo(captor.getAllValues().get(1).promptLayerBoundaries().layer1EndOffset());
  }

  @Test
  void promptLayerBoundaries_slicesMatchLayerContent() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    String agentBody = "AGENT_BOUNDARY_SLICE";
    String stepBody = "STEP_BOUNDARY_SLICE";
    AgentRepository repo = mock(AgentRepository.class);
    AgentDefinition agent = agentSupportingOnlyCompleteWithBody(agentBody);
    when(repo.get("agent-x")).thenReturn(agent);
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    CommandResponseSchema schema =
        CommandSchemaFactory.build(agent.supportedCommands(), mapper);
    String frameworkBlock = new CommandResponseSchemaRenderer().render(schema);
    String layerSeparator = System.lineSeparator() + System.lineSeparator();

    ContextRenderer contextRenderer = mock(ContextRenderer.class);
    when(contextRenderer.render(any(), any())).thenReturn("USER_DYNAMIC_INPUT_MARKER");

    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(contextRenderer)
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    WorkflowState state = workflowState("run-boundary-slices");
    invoker.invoke("agent-x", ContextMapping.none(), state, stepBody);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client).execute(captor.capture());
    LlmExecutionRequest request = captor.getValue();
    PromptLayerBoundaries boundaries = request.promptLayerBoundaries();
    byte[] promptBytes = request.systemPrompt().getBytes(StandardCharsets.UTF_8);

    assertThat(slicePrefix(promptBytes, boundaries.layer1EndOffset()))
        .isEqualTo(agentBody.getBytes(StandardCharsets.UTF_8));
    assertThat(slicePrefix(promptBytes, boundaries.layer2EndOffset()))
        .isEqualTo((agentBody + layerSeparator + frameworkBlock
            + layerSeparator + SystemRulesProvider.SYSTEM_RULES).getBytes(StandardCharsets.UTF_8));
    assertThat(slicePrefix(promptBytes, boundaries.layer3EndOffset()))
        .isEqualTo(promptBytes);
    assertThat(request.userInput()).isEqualTo("USER_DYNAMIC_INPUT_MARKER");
    assertThat(request.systemPrompt()).doesNotContain("USER_DYNAMIC_INPUT_MARKER");
  }

  @Test
  void promptLayerBoundaries_absentWhenCachingDisabled() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyCompleteWithBody("agent-disabled"));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    ContextRenderer contextRenderer = new ContextRenderer(mapper);
    LlmCommandParser commandParser = new LlmCommandParser(mapper);
    AgentInvoker enabledInvoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(contextRenderer)
        .llmCommandParser(commandParser)
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    AgentInvoker disabledInvoker = AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(contextRenderer)
        .llmCommandParser(commandParser)
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmOutputEventCharCap(AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(false)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
    WorkflowState enabledState = workflowState("run-cache-disabled-enabled");
    WorkflowState disabledState = workflowState("run-cache-disabled-disabled");
    String stepBody = "STEP_DISABLED_CACHE";

    enabledInvoker.invoke("agent-x", ContextMapping.none(), enabledState, stepBody);
    disabledInvoker.invoke("agent-x", ContextMapping.none(), disabledState, stepBody);

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    LlmExecutionRequest enabledRequest = captor.getAllValues().get(0);
    LlmExecutionRequest disabledRequest = captor.getAllValues().get(1);
    assertThat(enabledRequest.promptLayerBoundaries()).isNotNull();
    assertThat(disabledRequest.promptLayerBoundaries()).isNull();
    assertThat(disabledRequest.systemPrompt()).isEqualTo(enabledRequest.systemPrompt());
    assertThat(disabledRequest.userInput()).isEqualTo(enabledRequest.userInput());
    assertThat(disabledRequest.providerName()).isEqualTo(enabledRequest.providerName());
    assertThat(disabledRequest.model()).isEqualTo(enabledRequest.model());
    assertThat(disabledRequest.maxOutputTokens()).isEqualTo(enabledRequest.maxOutputTokens());
  }

  @Test
  void promptLayerBoundaries_layer3NullWhenStepPromptAbsent() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyCompleteWithBody("agent-no-step"));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentInvoker invoker = invokerWithAudit(mapper, client,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-no-layer3");

    invoker.invoke("agent-x", ContextMapping.none(), state, null);
    invoker.invoke("agent-x", ContextMapping.none(), state, "   ");

    ArgumentCaptor<LlmExecutionRequest> captor = ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(captor.capture());
    for (LlmExecutionRequest request : captor.getAllValues()) {
      PromptLayerBoundaries boundaries = request.promptLayerBoundaries();
      assertThat(boundaries.layer3EndOffset()).isNull();
      // No step layer: the prompt ends exactly at the framework layer, so layer 2 spans the
      // whole prompt.
      assertThat(boundaries.layer2EndOffset())
          .isEqualTo(request.systemPrompt().getBytes(StandardCharsets.UTF_8).length);
    }
  }

  @Test
  void invokeByAgentId_resolvesDefinitionFromAgentRepository() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    AgentRepository repo = mock(AgentRepository.class);
    AgentDefinition registered = agentSupportingOnlyCompleteWithBody("body");
    when(repo.get("lookup-id")).thenReturn(registered);
    EventRecorder eventRecorder = recorder(new InMemoryWorkflowEventLog());
    AgentInvoker invoker = AgentInvoker.builder()
        .agentRepository(repo)
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
    WorkflowState state = new WorkflowState("run-1", "wf-1", null,
        Instant.parse("2026-01-01T00:00:00Z"));
    invoker.invoke("lookup-id", ContextMapping.none(), state, null);
    verify(repo).get("lookup-id");
  }

  private static void assertScenarioAuditMatchesLlmCalls(
      ObjectMapper mapper, String runId, String firstRaw, int expectedLlmCalls) {
    assertScenarioAuditMatchesLlmCalls(mapper, runId, firstRaw, null, expectedLlmCalls, false);
  }

  private static void assertScenarioAuditMatchesLlmCalls(
      ObjectMapper mapper,
      String runId,
      String firstRaw,
      String secondRaw,
      int expectedLlmCalls) {
    assertScenarioAuditMatchesLlmCalls(mapper, runId, firstRaw, secondRaw, expectedLlmCalls, false);
  }

  private static void assertScenarioAuditMatchesLlmCalls(
      ObjectMapper mapper,
      String runId,
      String firstRaw,
      String secondRawOrNull,
      int expectedLlmCalls,
      boolean expectThrow) {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    if (secondRawOrNull == null) {
      when(client.execute(any())).thenReturn(llmResponse(firstRaw));
    } else {
      when(client.execute(any())).thenReturn(llmResponse(firstRaw), llmResponse(secondRawOrNull));
    }
    AgentInvoker invoker = invokerWithAudit(mapper, client, recorder);
    WorkflowState state = workflowState(runId);
    if (expectThrow) {
      assertThatThrownBy(() -> invoker.invoke("agent-x", ContextMapping.none(), state, null))
          .isInstanceOf(LlmCommandParseException.class);
    } else {
      invoker.invoke("agent-x", ContextMapping.none(), state, null);
    }
    verify(client, times(expectedLlmCalls)).execute(any());
    assertThat(llmOutputEventCount(eventLog, runId)).isEqualTo(expectedLlmCalls);
  }

  private static long llmOutputEventCount(InMemoryWorkflowEventLog eventLog, String runId) {
    return eventLog.getEvents(runId).stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .count();
  }

  private static EventRecorder recorder(InMemoryWorkflowEventLog eventLog) {
    return new EventRecorder(eventLog,
        Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC));
  }

  private static WorkflowState workflowState(String runId) {
    WorkflowState state = new WorkflowState(
        runId, "wf-1", null, Instant.parse("2026-01-01T00:00:00Z"));
    state.setCurrentStepId("step-1");
    return state;
  }

  private static AgentInvoker invokerWithAudit(
      ObjectMapper mapper, LlmClient client, EventRecorder recorder) {
    return invokerWithAudit(mapper, client, recorder, new ContextRenderer(mapper));
  }

  private static AgentInvoker invokerWithAudit(
      ObjectMapper mapper,
      LlmClient client,
      EventRecorder recorder,
      ContextRenderer contextRenderer) {
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyComplete());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    return AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(contextRenderer)
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  private static AgentInvoker invokerWithAudit(ObjectMapper mapper, LlmClientResolver resolver,
      EventRecorder recorder) {
    AgentRepository repo = mock(AgentRepository.class);
    return AgentInvoker.builder()
        .agentRepository(repo)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(recorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(recorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  private static AgentDefinition agentSupportingOnlyComplete() {
    return agentSupportingOnlyCompleteWithBody("sys");
  }

  private static LlmExecutionResponse llmResponse(String text) {
    return llmResponse(text, null, null);
  }

  private static LlmExecutionResponse llmResponse(
      String text, String modelUsed, TokenUsageReport tokenUsage) {
    return new LlmExecutionResponse(text, modelUsed, tokenUsage);
  }

  private static byte[] layerPrefixBytes(String systemPrompt, int endOffset) {
    return slicePrefix(systemPrompt.getBytes(StandardCharsets.UTF_8), endOffset);
  }

  private static byte[] slicePrefix(byte[] bytes, int endOffset) {
    return Arrays.copyOfRange(bytes, 0, endOffset);
  }

  private static AgentDefinition agentSupportingOnlyCompleteWithBody(String systemPrompt) {
    return AgentDefinition.builder()
        .withId("a1")
        .withName("A")
        .withLocality(AgentLocality.CLOUD)
        .withEnabled(true)
        .withSystemPrompt(systemPrompt)
        .withProviderPreferences(List.of(new ProviderPreference("openai", "gpt-4o-mini")))
        .withSupportedCommands(List.of("COMPLETE"))
        .withVersion("1.0.0")
        .build();
  }
}
