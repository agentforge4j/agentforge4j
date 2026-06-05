package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.ReservedContextKeys;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
  private final ObjectMapper objectMapper;

  public LlmCallObserver(EventRecorder eventRecorder, ObjectMapper objectMapper) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
  }

  /**
   * Typed shape of the {@link WorkflowEventType#LLM_CALL_COMPLETED} event payload. Serialized to JSON
   * by Jackson; field declaration order is the emitted key order. {@code modelSource} and
   * {@code requestedModelTier} serialize to their enum names ({@code null} stays {@code null}); all
   * token counts may be {@code null} when the provider reported no usage.
   *
   * @param agentId            the agent that triggered the call
   * @param provider           the provider name (e.g. {@code "claude"})
   * @param modelUsed          the concrete model the provider reported running; nullable
   * @param resolvedModel      the model the runtime resolved and sent; {@code null} for a provider
   *                           default
   * @param modelSource        how the model was determined (pin, tier, or provider default)
   * @param requestedModelTier the requested capability tier, or {@code null} when none applied
   * @param inputTokens        prompt token count, or {@code null} when not reported
   * @param outputTokens       completion token count, or {@code null} when not reported
   * @param totalTokens        {@code inputTokens + outputTokens}, or {@code null} when neither was
   *                           reported
   */
  public record LlmCallCompletedPayload(
      String agentId,
      String provider,
      String modelUsed,
      String resolvedModel,
      ModelSource modelSource,
      ModelTier requestedModelTier,
      Integer inputTokens,
      Integer outputTokens,
      Integer totalTokens) {
  }

  /**
   * Called once per completed LLM provider call.
   *
   * @param agentId            the agent that triggered the call
   * @param provider           the provider name (e.g. {@code "claude"})
   * @param response           the full provider response including usage and model metadata
   * @param resolvedModel      the model the runtime resolved and sent; {@code null} when the
   *                           provider default was used
   * @param modelSource        how the model was determined (pin, tier, or provider default)
   * @param requestedModelTier the requested capability tier, or {@code null} when none applied
   * @param state              mutable run state — token total is read-add-written here
   */
  public void observe(String agentId,
      String provider,
      LlmExecutionResponse response,
      String resolvedModel,
      ModelSource modelSource,
      ModelTier requestedModelTier,
      WorkflowState state) {
    TokenUsageReport tokenUsage = response.tokenUsage();
    int callTotal = computeCallTokenTotal(tokenUsage);
    String payload = buildPayload(agentId, provider, response.modelUsed(), resolvedModel,
        modelSource, requestedModelTier, tokenUsage, callTotal);
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

  private String buildPayload(String agentId,
      String provider,
      String modelUsed,
      String resolvedModel,
      ModelSource modelSource,
      ModelTier requestedModelTier,
      TokenUsageReport tokenUsage,
      int callTotal) {
    // totalTokens = inputTokens + outputTokens; TokenUsageReport has no totalTokens field by design
    Integer totalTokens = (tokenUsage == null
        || (tokenUsage.inputTokens() == null && tokenUsage.outputTokens() == null))
        ? null : callTotal;
    LlmCallCompletedPayload payload = new LlmCallCompletedPayload(
        agentId,
        provider,
        modelUsed,
        resolvedModel,
        modelSource,
        requestedModelTier,
        tokenUsage == null ? null : tokenUsage.inputTokens(),
        tokenUsage == null ? null : tokenUsage.outputTokens(),
        totalTokens);
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize LLM_CALL_COMPLETED payload for agent: %s".formatted(agentId), e);
    }
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
}
