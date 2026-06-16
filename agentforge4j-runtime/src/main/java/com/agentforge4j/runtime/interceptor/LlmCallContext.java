package com.agentforge4j.runtime.interceptor;

import com.agentforge4j.util.Validate;

/**
 * Neutral context for {@link RunExecutionInterceptor#beforeLlmCall(LlmCallContext)}, supplied immediately before each
 * LLM provider call (after model resolution, before dispatch).
 *
 * <p>Deliberately carries no prompt text: an embedding application that needs to size a worst-case
 * cost envelope gets the assembled prompt's character {@link #assembledPromptLength() length} (a plain
 * {@code String.length()}, not a tokenizer) and applies its own heuristic. Keeping OSS tokenizer-free and not spraying
 * prompt content across this control SPI is intentional. Input-token counts are not known before the call
 * ({@link #cachedInputUnknown()} is therefore always {@code true} here); the actual usage is reconciled after the call
 * via the existing {@code LlmCallObserver} / {@code LLM_CALL_COMPLETED} event.
 *
 * @param runId                 owning run id; never blank
 * @param stepId                step the call belongs to; may be {@code null}/blank when not step-scoped
 * @param agentId               the agent making the call; never blank
 * @param provider              the resolved provider name (e.g. {@code "claude"}); never blank
 * @param resolvedModel         the model the runtime resolved and will send; may be {@code null} for a provider
 *                              default
 * @param maxOutputTokens       the request's generated-token cap, or {@code null} when unset (the embedding application
 *                              falls back to its governing default)
 * @param assembledPromptLength character length of the assembled system prompt plus user input ({@code >= 0}); a
 *                              length, not a token count
 * @param cachedInputUnknown    whether cached-input status is unknown at this point (always {@code true} pre-call —
 *                              size against the non-cached ceiling)
 */
public record LlmCallContext(
    String runId,
    String stepId,
    String agentId,
    String provider,
    String resolvedModel,
    Integer maxOutputTokens,
    int assembledPromptLength,
    boolean cachedInputUnknown) {

  public LlmCallContext {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(agentId, "agentId must not be blank");
    Validate.notBlank(provider, "provider must not be blank");
    Validate.isTrue(maxOutputTokens == null || maxOutputTokens > 0, "maxOutputTokens must be greater than 0 when set");
    Validate.isNotNegative(assembledPromptLength, "assembledPromptLength must not be negative");
  }
}
