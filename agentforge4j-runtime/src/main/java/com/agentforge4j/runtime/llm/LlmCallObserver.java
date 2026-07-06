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
   * @param cachedTokens       prompt-cache input tokens the provider reported, or {@code null} when not reported
   * @param stepUid            the current step's dispatch execution uid, or {@code null} when the step has
   *                           no recorded dispatch uid; disambiguates repeated dispatches of the same
   *                           stepId (loop iterations, retries) from one another
   * @param callAttempt        1-based ordinal of this call within its dispatch; a schema-parse-retried
   *                           step makes multiple calls under one {@code stepUid}, each with its own
   *                           {@code callAttempt}
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
      Integer totalTokens,
      Integer cachedTokens,
      String stepUid,
      Integer callAttempt) {
  }

  /**
   * Called once for the LLM call whose parsed output the workflow actually uses. Emits the audit event
   * and updates the {@link ReservedContextKeys#LLM_TOKENS_TOTAL} running total.
   *
   * @param agentId            the agent that triggered the call
   * @param provider           the provider name (e.g. {@code "claude"})
   * @param response           the full provider response including usage and model metadata
   * @param resolvedModel      the model the runtime resolved and sent; {@code null} when the
   *                           provider default was used
   * @param modelSource        how the model was determined (pin, tier, or provider default)
   * @param requestedModelTier the requested capability tier, or {@code null} when none applied
   * @param state              mutable run state — token total is read-add-written here
   * @param attempt            1-based ordinal of this call within its dispatch
   */
  public void observe(String agentId,
      String provider,
      LlmExecutionResponse response,
      String resolvedModel,
      ModelSource modelSource,
      ModelTier requestedModelTier,
      WorkflowState state,
      int attempt) {
    int callTotal = emit(agentId, provider, response, resolvedModel, modelSource,
        requestedModelTier, state, attempt);
    accumulateTokens(state, callTotal);
  }

  /**
   * Records a discarded LLM call attempt for audit/billing purposes only. A schema-parse-retried
   * attempt still consumed real, billable provider tokens even though its output was rejected and
   * superseded by a later attempt in the same dispatch. Unlike {@link #observe}, this does not update
   * {@link ReservedContextKeys#LLM_TOKENS_TOTAL} — that running total tracks only the response the
   * workflow actually used, which {@link #observe} records exactly once per dispatch.
   *
   * @param agentId            the agent that triggered the call
   * @param provider           the provider name (e.g. {@code "claude"})
   * @param response           the full provider response including usage and model metadata
   * @param resolvedModel      the model the runtime resolved and sent; {@code null} when the
   *                           provider default was used
   * @param modelSource        how the model was determined (pin, tier, or provider default)
   * @param requestedModelTier the requested capability tier, or {@code null} when none applied
   * @param state              mutable run state
   * @param attempt            1-based ordinal of this call within its dispatch
   */
  public void recordAttempt(String agentId,
      String provider,
      LlmExecutionResponse response,
      String resolvedModel,
      ModelSource modelSource,
      ModelTier requestedModelTier,
      WorkflowState state,
      int attempt) {
    emit(agentId, provider, response, resolvedModel, modelSource, requestedModelTier, state,
        attempt);
  }

  private int emit(String agentId,
      String provider,
      LlmExecutionResponse response,
      String resolvedModel,
      ModelSource modelSource,
      ModelTier requestedModelTier,
      WorkflowState state,
      int attempt) {
    TokenUsageReport tokenUsage = response.tokenUsage();
    int callTotal = computeCallTokenTotal(tokenUsage);
    String stepUid = stepUidFor(state);
    String payload = buildPayload(agentId, provider, response.modelUsed(), resolvedModel,
        modelSource, requestedModelTier, tokenUsage, callTotal, stepUid, attempt);
    eventRecorder.record(
        state.getRunId(),
        state.getCurrentStepId(),
        WorkflowEventType.LLM_CALL_COMPLETED,
        payload,
        "runtime");
    return callTotal;
  }

  private static String stepUidFor(WorkflowState state) {
    Integer uid = state.getStepExecutionUid().get(state.getCurrentStepId());
    return uid == null ? null : String.valueOf(uid);
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
      int callTotal,
      String stepUid,
      int callAttempt) {
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
        totalTokens,
        tokenUsage == null ? null : tokenUsage.cachedInputTokens(),
        stepUid,
        callAttempt);
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
        new NumberContextValue(runningTotal + callTotal, ContextProvenance.SYSTEM_GENERATED));
    Integer currentStepUid = state.getStepExecutionUid().get(state.getCurrentStepId());
    if (currentStepUid != null) {
      state.putContextKeyWrittenAtUid(ReservedContextKeys.LLM_TOKENS_TOTAL, currentStepUid);
    }
  }
}
