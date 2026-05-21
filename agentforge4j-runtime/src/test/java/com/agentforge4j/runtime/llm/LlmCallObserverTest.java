package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;

class LlmCallObserverTest {

  @Test
  void observe_emits_LLM_CALL_COMPLETED_event() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmCallObserver observer = new LlmCallObserver(recorder);
    WorkflowState state = workflowState("run-llm-call-completed");

    observer.observe(
        "agent-x",
        "openai",
        llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        state);

    var completedEvents = eventLog.getEvents("run-llm-call-completed").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .toList();
    assertThat(completedEvents).hasSize(1);
    assertThat(completedEvents.get(0).actorId()).isEqualTo("runtime");

    String payload = completedEvents.get(0).payload();
    assertThat(payload).contains("\"agentId\":\"agent-x\"");
    assertThat(payload).contains("\"provider\":\"openai\"");
    assertThat(payload).contains("\"inputTokens\":100");
    assertThat(payload).contains("\"outputTokens\":50");
    assertThat(payload).contains("\"totalTokens\":150");
  }

  @Test
  void observe_writes_llm_tokens_total_to_context() {
    LlmCallObserver observer = new LlmCallObserver(recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-llm-tokens-total");

    observer.observe(
        "agent-x",
        "openai",
        llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        state);

    assertThat(llmTokensTotalInContext(state)).isEqualTo(150);
  }

  @Test
  void observe_accumulates_across_multiple_calls() {
    LlmCallObserver observer = new LlmCallObserver(recorder(new InMemoryWorkflowEventLog()));
    WorkflowState state = workflowState("run-llm-tokens-accumulate");
    TokenUsageReport firstUsage = new TokenUsageReport(10, 5, null, null);
    TokenUsageReport secondUsage = new TokenUsageReport(20, 8, null, null);

    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", firstUsage), state);
    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", secondUsage), state);

    assertThat(llmTokensTotalInContext(state)).isEqualTo(43);
  }

  @Test
  void observe_with_null_tokenUsage_writes_zero_contribution() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmCallObserver observer = new LlmCallObserver(recorder);
    WorkflowState state = workflowState("run-null-usage-contribution");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o", null), state);

    assertThat(llmTokensTotalInContext(state)).isEqualTo(0);
    assertThat(eventLog.getEvents("run-null-usage-contribution").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .count()).isEqualTo(1);
  }

  @Test
  void observe_payload_emits_null_for_absent_tokens() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmCallObserver observer = new LlmCallObserver(recorder);
    WorkflowState state = workflowState("run-null-token-payload");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o", null), state);

    String payload = eventLog.getEvents("run-null-token-payload").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).contains("\"inputTokens\":null");
    assertThat(payload).contains("\"outputTokens\":null");
    assertThat(payload).contains("\"totalTokens\":null");
  }

  private static int llmTokensTotalInContext(WorkflowState state) {
    ContextValue value = state.getContext().get(ReservedContextKeys.LLM_TOKENS_TOTAL);
    assertThat(value).isInstanceOf(NumberContextValue.class);
    return ((NumberContextValue) value).value().intValue();
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

  private static LlmExecutionResponse llmResponse(String modelUsed, TokenUsageReport tokenUsage) {
    return new LlmExecutionResponse("[{\"type\":\"COMPLETE\"}]", modelUsed, tokenUsage);
  }
}
