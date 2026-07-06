// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;

class LlmCallObserverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void observe_emits_LLM_CALL_COMPLETED_event() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmCallObserver observer = new LlmCallObserver(recorder, objectMapper);
    WorkflowState state = workflowState("run-llm-call-completed");

    observer.observe(
        "agent-x",
        "openai",
        llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        "gpt-4o-mini",
        ModelSource.TIER,
        ModelTier.STANDARD,
        state,
        1);

    var completedEvents = eventLog.getEvents("run-llm-call-completed").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .toList();
    assertThat(completedEvents).hasSize(1);
    assertThat(completedEvents.get(0).actorId()).isEqualTo("runtime");

    String payload = completedEvents.get(0).payload();
    assertThat(payload).contains("\"agentId\":\"agent-x\"");
    assertThat(payload).contains("\"provider\":\"openai\"");
    assertThat(payload).contains("\"resolvedModel\":\"gpt-4o-mini\"");
    assertThat(payload).contains("\"modelSource\":\"TIER\"");
    assertThat(payload).contains("\"requestedModelTier\":\"STANDARD\"");
    assertThat(payload).contains("\"inputTokens\":100");
    assertThat(payload).contains("\"outputTokens\":50");
    assertThat(payload).contains("\"totalTokens\":150");
  }

  @Test
  void observe_writes_llm_tokens_total_to_context() {
    LlmCallObserver observer = new LlmCallObserver(recorder(new InMemoryWorkflowEventLog()), objectMapper);
    WorkflowState state = workflowState("run-llm-tokens-total");

    observer.observe(
        "agent-x",
        "openai",
        llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        null,
        ModelSource.PROVIDER_DEFAULT,
        null,
        state,
        1);

    assertThat(llmTokensTotalInContext(state)).isEqualTo(150);
  }

  @Test
  void observe_accumulates_across_multiple_calls() {
    LlmCallObserver observer = new LlmCallObserver(recorder(new InMemoryWorkflowEventLog()), objectMapper);
    WorkflowState state = workflowState("run-llm-tokens-accumulate");
    TokenUsageReport firstUsage = new TokenUsageReport(10, 5, null, null);
    TokenUsageReport secondUsage = new TokenUsageReport(20, 8, null, null);

    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", firstUsage),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);
    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", secondUsage),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);

    assertThat(llmTokensTotalInContext(state)).isEqualTo(43);
  }

  @Test
  void observe_with_null_tokenUsage_writes_zero_contribution() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmCallObserver observer = new LlmCallObserver(recorder, objectMapper);
    WorkflowState state = workflowState("run-null-usage-contribution");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o", null),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);

    assertThat(llmTokensTotalInContext(state)).isEqualTo(0);
    assertThat(eventLog.getEvents("run-null-usage-contribution").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .count()).isEqualTo(1);
  }

  @Test
  void observe_payload_emits_null_for_absent_tokens() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder recorder = recorder(eventLog);
    LlmCallObserver observer = new LlmCallObserver(recorder, objectMapper);
    WorkflowState state = workflowState("run-null-token-payload");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o", null),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);

    String payload = eventLog.getEvents("run-null-token-payload").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).contains("\"inputTokens\":null");
    assertThat(payload).contains("\"outputTokens\":null");
    assertThat(payload).contains("\"totalTokens\":null");
    assertThat(payload).contains("\"cachedTokens\":null");
  }

  @Test
  void observe_payload_includes_cachedTokens_when_reported() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    LlmCallObserver observer = new LlmCallObserver(recorder(eventLog), objectMapper);
    WorkflowState state = workflowState("run-cached-tokens");

    observer.observe("agent-x", "openai",
        llmResponse("gpt-4o-mini", new TokenUsageReport(100, 50, 30, 10)),
        "gpt-4o-mini", ModelSource.TIER, ModelTier.STANDARD, state, 1);

    String payload = eventLog.getEvents("run-cached-tokens").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .findFirst()
        .orElseThrow()
        .payload();
    assertThat(payload).contains("\"cachedTokens\":30");
  }

  @Test
  void observe_stamps_llm_tokens_total_as_system_generated() {
    LlmCallObserver observer = new LlmCallObserver(recorder(new InMemoryWorkflowEventLog()), objectMapper);
    WorkflowState state = workflowState("run-llm-tokens-provenance");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);

    ContextValue value = state.getContext().get(ReservedContextKeys.LLM_TOKENS_TOTAL);
    assertThat(value.provenance()).isEqualTo(ContextProvenance.SYSTEM_GENERATED);
  }

  @Test
  void observe_payload_includes_stepUid_when_a_dispatch_uid_is_recorded() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    LlmCallObserver observer = new LlmCallObserver(recorder(eventLog), objectMapper);
    WorkflowState state = workflowState("run-step-uid");
    state.putStepExecutionUid("step-1", 42);

    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);

    String payload = onlyCompletedEventPayload(eventLog, "run-step-uid");
    assertThat(payload).contains("\"stepUid\":\"42\"");
  }

  @Test
  void observe_payload_stepUid_is_null_when_no_dispatch_uid_is_recorded() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    LlmCallObserver observer = new LlmCallObserver(recorder(eventLog), objectMapper);
    WorkflowState state = workflowState("run-no-step-uid");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);

    String payload = onlyCompletedEventPayload(eventLog, "run-no-step-uid");
    assertThat(payload).contains("\"stepUid\":null");
  }

  @Test
  void observe_payload_carries_the_call_attempt_ordinal() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    LlmCallObserver observer = new LlmCallObserver(recorder(eventLog), objectMapper);
    WorkflowState state = workflowState("run-call-attempt");

    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 2);

    String payload = onlyCompletedEventPayload(eventLog, "run-call-attempt");
    assertThat(payload).contains("\"callAttempt\":2");
  }

  @Test
  void recordAttempt_emits_an_event_but_does_not_accumulate_the_token_total() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    LlmCallObserver observer = new LlmCallObserver(recorder(eventLog), objectMapper);
    WorkflowState state = workflowState("run-discarded-attempt");
    state.putStepExecutionUid("step-1", 7);

    observer.recordAttempt("agent-x", "openai", llmResponse("gpt-4o-mini", TEST_TOKEN_USAGE),
        "gpt-4o-mini", ModelSource.TIER, ModelTier.STANDARD, state, 1);

    String payload = onlyCompletedEventPayload(eventLog, "run-discarded-attempt");
    assertThat(payload).contains("\"stepUid\":\"7\"");
    assertThat(payload).contains("\"callAttempt\":1");
    assertThat(payload).contains("\"inputTokens\":100");
    assertThat(state.getContext().get(ReservedContextKeys.LLM_TOKENS_TOTAL)).isNull();
  }

  @Test
  void recordAttempt_followed_by_observe_emits_two_events_but_accumulates_only_the_winning_call() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    LlmCallObserver observer = new LlmCallObserver(recorder(eventLog), objectMapper);
    WorkflowState state = workflowState("run-parse-retry-billing");
    state.putStepExecutionUid("step-1", 7);
    TokenUsageReport discardedUsage = new TokenUsageReport(10, 5, null, null);
    TokenUsageReport winningUsage = new TokenUsageReport(20, 8, null, null);

    observer.recordAttempt("agent-x", "openai", llmResponse("gpt-4o-mini", discardedUsage),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 1);
    observer.observe("agent-x", "openai", llmResponse("gpt-4o-mini", winningUsage),
        null, ModelSource.PROVIDER_DEFAULT, null, state, 2);

    var completedEvents = eventLog.getEvents("run-parse-retry-billing").stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .toList();
    assertThat(completedEvents).hasSize(2);
    assertThat(completedEvents.get(0).payload()).contains("\"callAttempt\":1");
    assertThat(completedEvents.get(1).payload()).contains("\"callAttempt\":2");
    // Only the winning (observe) call's tokens land in the running total — the discarded attempt's
    // tokens are billed via its own event, not double-counted into the workflow's own bookkeeping.
    assertThat(llmTokensTotalInContext(state)).isEqualTo(28);
  }

  private static String onlyCompletedEventPayload(InMemoryWorkflowEventLog eventLog, String runId) {
    return eventLog.getEvents(runId).stream()
        .filter(e -> e.eventType() == WorkflowEventType.LLM_CALL_COMPLETED)
        .findFirst()
        .orElseThrow()
        .payload();
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
