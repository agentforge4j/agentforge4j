package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;

/**
 * Immutable parameters for a single LLM invocation.
 *
 * <p>Construct via the canonical constructor — optional fields ({@code maxOutputTokens},
 * {@code promptLayerBoundaries}, {@code identity}) are passed explicitly as {@code null} when absent,
 * and a {@code null} {@code model} selects the client's configured default. There are no convenience
 * factories: while the API is pre-{@code 0.1.0} we keep one canonical signature rather than shims.
 *
 * @param providerName          provider id (for example {@code "openai"}); must match the target {@link LlmClient}
 * @param model                 model id for this call, or {@code null} to use the client's configured default
 * @param systemPrompt          system instructions for the model
 * @param userInput             user or tool-facing content for this turn
 * @param maxOutputTokens       optional cap on generated tokens (provider-specific; ignored when {@code null})
 * @param promptLayerBoundaries stable-prefix layer byte boundaries for prompt caching, or {@code null} when caching is
 *                              disabled or boundaries are unknown
 * @param identity              optional originating run/workflow/step/agent identity, or {@code null} for direct,
 *                              run-less use; real providers ignore it
 */
public record LlmExecutionRequest(
    String providerName,
    String model,
    String systemPrompt,
    String userInput,
    Integer maxOutputTokens,
    PromptLayerBoundaries promptLayerBoundaries,
    LlmInvocationIdentity identity) {

  /**
   * Validates that required fields are not blank and optional token cap is positive when set.
   *
   * @throws IllegalArgumentException if any required field is blank
   */
  public LlmExecutionRequest {
    providerName = Validate.notBlank(providerName, "Provider must not be blank");
    systemPrompt = Validate.notBlank(systemPrompt, "System prompt must not be blank");
    userInput = Validate.notBlank(userInput, "User input must not be blank");
    Validate.isTrue(maxOutputTokens == null || maxOutputTokens > 0, "maxOutputTokens must be greater than 0 when set");
  }
}
