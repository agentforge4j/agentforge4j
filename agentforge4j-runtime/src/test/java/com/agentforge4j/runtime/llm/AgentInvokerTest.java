package com.agentforge4j.runtime.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.AgentLocality;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.schema.CommandResponseSchemaRenderer;
import com.agentforge4j.core.command.schema.CommandSchemaFactory;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentInvokerTest {

  @Test
  void parseRetryPipeline_successfulFirstParse_oneLlmCall_oneAudit_noDuplicates() {
    ObjectMapper mapper = new ObjectMapper();
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    String runId = "run-first-ok";
    String raw = "[{\"type\":\"COMPLETE\"}]";

    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn(raw);

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
    when(client.execute(any())).thenReturn(firstRaw, secondRaw);

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
    when(client.execute(any())).thenReturn(bad.strip(), bad.strip());

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
    when(client.execute(any())).thenReturn("{}", "[{\"type\":\"COMPLETE\"}]");

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
    when(client.execute(any())).thenReturn(raw);

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
    when(client.execute(any())).thenReturn(raw);

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
    assertThat(payload).startsWith(raw.substring(0, AgentInvoker.DEFAULT_LLM_OUTPUT_EVENT_CHAR_CAP));
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
    when(client.execute(any())).thenReturn(raw);

    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyComplete());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);

    AgentInvoker invoker = new AgentInvoker(
        repo, resolver, new ContextRenderer(mapper), new LlmCommandParser(mapper), mapper, recorder, 0);
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
    when(client.execute(any())).thenReturn(raw);

    AgentRepository repo = mock(AgentRepository.class);
    when(repo.get("agent-x")).thenReturn(agentSupportingOnlyComplete());
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);

    AgentInvoker invoker = new AgentInvoker(
        repo, resolver, new ContextRenderer(mapper), new LlmCommandParser(mapper), mapper, recorder, 100);
    WorkflowState state = workflowState("run-cap-custom");
    invoker.invoke("agent-x", ContextMapping.none(), state, null);

    String payload = eventLog.getEvents("run-cap-custom").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_OUTPUT)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).hasSize(100 + "... [event payload truncated for audit; original length=".length()
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
  void invokeByAgentId_resolvesDefinitionFromAgentRepository() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(client.execute(any())).thenReturn("[{\"type\":\"COMPLETE\"}]");
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    when(resolver.resolve("openai")).thenReturn(client);
    when(resolver.isProviderAvailable("openai")).thenReturn(true);
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
      when(client.execute(any())).thenReturn(firstRaw);
    } else {
      when(client.execute(any())).thenReturn(firstRaw, secondRawOrNull);
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
