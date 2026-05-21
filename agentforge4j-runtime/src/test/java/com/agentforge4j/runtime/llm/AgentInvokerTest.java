package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchema;
import com.agentforge4j.core.command.schema.CommandResponseSchemaRenderer;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
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

    var llmOutputs = eventLog.getEvents(runId).stream()
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

    AgentInvoker invoker = new AgentInvoker(
        repo, resolver, new ContextRenderer(mapper), new LlmCommandParser(mapper), mapper, recorder,
        0);
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

    AgentInvoker invoker = new AgentInvoker(
        repo, resolver, new ContextRenderer(mapper), new LlmCommandParser(mapper), mapper, recorder,
        100);
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
  void constructor_rejects_negative_cap() {
    assertThatThrownBy(() -> new AgentInvoker(
        mock(AgentRepository.class),
        mock(LlmClientResolver.class),
        new ContextRenderer(new ObjectMapper()),
        new LlmCommandParser(new ObjectMapper()),
        new ObjectMapper(),
        recorder(new InMemoryWorkflowEventLog()),
        -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invoke_usesInjectedProviderSelectionStrategy() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("ollama");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    AgentRepository repo = mock(AgentRepository.class);
    AgentDefinition agent = new AgentDefinition(
        "agent-x",
        "A",
        com.agentforge4j.core.agent.AgentLocality.CLOUD,
        true,
        "sys",
        List.of(
            new ProviderPreference("openai", "gpt-4o"),
            new ProviderPreference("ollama", "llama3")),
        List.of("COMPLETE"),
        null,
        null,
        "1.0.0");
    when(repo.get("agent-x")).thenReturn(agent);

    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("ollama")).thenReturn(client);
    when(resolver.listAvailableClients()).thenReturn(List.of("ollama"));

    ProviderPreference strategyChoice = new ProviderPreference("ollama", "llama3");
    LlmProviderSelectionStrategy selectionStrategy = mock(LlmProviderSelectionStrategy.class);
    when(selectionStrategy.selectInitialProvider(agent, List.of("ollama"))).thenReturn(
        strategyChoice);

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()),
        AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP,
        selectionStrategy);

    WorkflowState state = workflowState("run-strategy");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    verify(selectionStrategy).selectInitialProvider(agent, List.of("ollama"));
    verify(resolver).resolve("ollama");
    verify(client).execute(any());
  }

  @Test
  void constructor_rejectsNullProviderSelectionStrategy() {
    assertThatThrownBy(() -> new AgentInvoker(
        mock(AgentRepository.class),
        mock(LlmClientResolver.class),
        new ContextRenderer(new ObjectMapper()),
        new LlmCommandParser(new ObjectMapper()),
        new ObjectMapper(),
        recorder(new InMemoryWorkflowEventLog()),
        AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP,
        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("llmProviderSelectionStrategy must not be null");
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

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()));
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
  void assembledSystemPrompt_omitsBlankOrAbsentStepLayer() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(llmResponse("[{\"type\":\"COMPLETE\"}]"));

    String agentBody = "AGENT_ONLY_LAYER";
    String stepBody = "STEP_SHOULD_NOT_APPEAR";
    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyCompleteWithBody(agentBody));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-system-prompt-no-step");

    invoker.invoke("agent-x", ContextMapping.none(), state, null);
    ArgumentCaptor<LlmExecutionRequest> nullStepCaptor =
        ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(1)).execute(nullStepCaptor.capture());
    String nullStepPrompt = nullStepCaptor.getValue().systemPrompt();
    assertThat(nullStepPrompt).doesNotContain(stepBody);
    assertThat(layerSeparatorCount(nullStepPrompt)).isEqualTo(1);

    invoker.invoke("agent-x", ContextMapping.none(), state, "   ");
    ArgumentCaptor<LlmExecutionRequest> blankStepCaptor =
        ArgumentCaptor.forClass(LlmExecutionRequest.class);
    verify(client, times(2)).execute(blankStepCaptor.capture());
    String blankStepPrompt = blankStepCaptor.getAllValues().get(1).systemPrompt();
    assertThat(blankStepPrompt).doesNotContain(stepBody);
    assertThat(layerSeparatorCount(blankStepPrompt)).isEqualTo(1);
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
        + stepBody;

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()));
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

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()),
        AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP,
        new FirstAvailableProviderSelectionStrategy(),
        true);
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
    when(repo.get("agent-b")).thenReturn(new AgentDefinition(
        "agent-b",
        "B",
        AgentLocality.CLOUD,
        true,
        "agent-a-body",
        List.of(new ProviderPreference("openai", "gpt-4o-mini")),
        List.of("COMPLETE"),
        null,
        null,
        "1.0.0"));
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()));
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

    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        contextRenderer,
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()));
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
        .isEqualTo((agentBody + layerSeparator + frameworkBlock).getBytes(StandardCharsets.UTF_8));
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
    AgentInvoker enabledInvoker = new AgentInvoker(
        repo, resolver, contextRenderer, commandParser, mapper, recorder);
    AgentInvoker disabledInvoker = new AgentInvoker(
        repo,
        resolver,
        contextRenderer,
        commandParser,
        mapper,
        recorder,
        AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP,
        new FirstAvailableProviderSelectionStrategy(),
        false);
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
      assertThat(boundaries.layer2EndOffset())
          .isEqualTo(request.systemPrompt().getBytes(StandardCharsets.UTF_8).length);
      assertThat(layerSeparatorCount(request.systemPrompt())).isEqualTo(1);
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
    AgentInvoker invoker = new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder(new InMemoryWorkflowEventLog()));
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
    return new AgentInvoker(
        repo,
        resolver,
        contextRenderer,
        new LlmCommandParser(mapper),
        mapper,
        recorder);
  }

  private static AgentInvoker invokerWithAudit(ObjectMapper mapper, LlmClientResolver resolver,
      EventRecorder recorder) {
    AgentRepository repo = mock(AgentRepository.class);
    return new AgentInvoker(
        repo,
        resolver,
        new ContextRenderer(mapper),
        new LlmCommandParser(mapper),
        mapper,
        recorder);
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

  private static int layerSeparatorCount(String text) {
    String layerSeparator = System.lineSeparator() + System.lineSeparator();
    int count = 0;
    int from = 0;
    while (true) {
      int idx = text.indexOf(layerSeparator, from);
      if (idx < 0) {
        return count;
      }
      count++;
      from = idx + layerSeparator.length();
    }
  }

  private static AgentDefinition agentSupportingOnlyCompleteWithBody(String systemPrompt) {
    return new AgentDefinition(
        "a1",
        "A",
        AgentLocality.CLOUD,
        true,
        systemPrompt,
        List.of(new ProviderPreference("openai", "gpt-4o-mini")),
        List.of("COMPLETE"),
        null,
        null,
        "1.0.0");
  }
}
