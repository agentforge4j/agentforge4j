package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;

/**
 * Observes each completed LLM call: emits a {@link WorkflowEventType#LLM_CALL_COMPLETED} event and
 * maintains the {@link ReservedContextKeys#LLM_TOKENS_TOTAL} running total in {@link WorkflowState}
 * context.
 *
 * <p>Separating this concern from {@link AgentInvoker} keeps the invoker focused on
 * dispatch and retry while token accounting and audit emission are independently testable.
 */
public final class LlmCallObserver {

  private final EventRecorder eventRecorder;

  public LlmCallObserver(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  /**
   * Called once per completed LLM provider call.
   *
   * @param agentId  the agent that triggered the call
   * @param provider the provider name (e.g. {@code "claude"})
   * @param response the full provider response including usage and model metadata
   * @param state    mutable run state — token total is read-add-written here
   */
  public void observe(String agentId,
      String provider,
      LlmExecutionResponse response,
      WorkflowState state) {
    TokenUsageReport tokenUsage = response.tokenUsage();
    int callTotal = computeCallTokenTotal(tokenUsage);
    String payload = buildPayload(agentId, provider, response.modelUsed(), tokenUsage, callTotal);
    eventRecorder.record(
        state.getRunId(),
        state.getCurrentStepId(),
        WorkflowEventType.LLM_CALL_COMPLETED,
        payload,
        "runtime");
    accumulateTokens(state, callTotal);
  }

  private static int computeCallTokenTotal(TokenUsageReport tokenUsage) {
    if (tokenUsage == null) {
      return 0;
    }
    int input = tokenUsage.inputTokens() == null ? 0 : tokenUsage.inputTokens();
    int output = tokenUsage.outputTokens() == null ? 0 : tokenUsage.outputTokens();
    return input + output;
  }

  private static String buildPayload(String agentId,
      String provider,
      String modelUsed,
      TokenUsageReport tokenUsage,
      int callTotal) {
    // totalTokens = inputTokens + outputTokens; TokenUsageReport has no totalTokens field by design
    Integer totalTokens = (tokenUsage == null
        || (tokenUsage.inputTokens() == null && tokenUsage.outputTokens() == null))
        ? null : callTotal;
    return "{\"agentId\":%s,\"provider\":%s,\"modelUsed\":%s,\"inputTokens\":%s,\"outputTokens\":%s,\"totalTokens\":%s}"
        .formatted(
            jsonString(agentId),
            jsonString(provider),
            jsonString(modelUsed),
            jsonNumber(tokenUsage == null ? null : tokenUsage.inputTokens()),
            jsonNumber(tokenUsage == null ? null : tokenUsage.outputTokens()),
            jsonNumber(totalTokens));
  }

  private void accumulateTokens(WorkflowState state, int callTotal) {
    ContextValue existing = state.getContext().get(ReservedContextKeys.LLM_TOKENS_TOTAL);
    int runningTotal = 0;
    if (existing instanceof NumberContextValue numberContextValue) {
      Number value = numberContextValue.value();
      if (value != null) {
        runningTotal = value.intValue();
      }
    }
    state.putContextValue(
        ReservedContextKeys.LLM_TOKENS_TOTAL,
        new NumberContextValue(runningTotal + callTotal));
    Integer currentStepUid = state.getStepExecutionUid().get(state.getCurrentStepId());
    if (currentStepUid != null) {
      state.putContextKeyWrittenAtUid(ReservedContextKeys.LLM_TOKENS_TOTAL, currentStepUid);
    }
  }

  private static String jsonString(String value) {
    return value == null ? "null"
        : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String jsonNumber(Integer value) {
    return value == null ? "null" : value.toString();
  }
}
