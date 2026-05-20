package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;

/**
 * Immutable parameters for a single LLM invocation.
 *
 * @param providerName          provider id (for example {@code "openai"}); must match the target
 *                              {@link LlmClient}
 * @param model                 model id for this call, or {@code null} to use the client's
 *                              configured default
 * @param systemPrompt          system instructions for the model
 * @param userInput             user or tool-facing content for this turn
 * @param maxOutputTokens       optional cap on generated tokens (provider-specific; ignored when
 *                              {@code null})
 * @param promptLayerBoundaries stable-prefix layer byte boundaries for prompt caching, or
 *                              {@code null} when caching is disabled or boundaries are unknown
 */
public record LlmExecutionRequest(
    String providerName,
    String model,
    String systemPrompt,
    String userInput,
    Integer maxOutputTokens,
    PromptLayerBoundaries promptLayerBoundaries) {

  public LlmExecutionRequest(String providerName, String model, String systemPrompt,
      String userInput) {
    this(providerName, model, systemPrompt, userInput, null, null);
  }

  /**
   * Creates a request without prompt-layer boundaries.
   *
   * @param providerName    target provider id
   * @param model           model id, or {@code null} for the client default
   * @param systemPrompt    system instructions
   * @param userInput       user content for this turn
   * @param maxOutputTokens optional generated-token cap
   */
  public LlmExecutionRequest(String providerName, String model, String systemPrompt,
      String userInput, Integer maxOutputTokens) {
    this(providerName, model, systemPrompt, userInput, maxOutputTokens, null);
  }

  /**
   * Validates that required fields are not blank and optional token cap is positive when set.
   *
   * @throws IllegalArgumentException if any required field is blank
   */
  public LlmExecutionRequest {
    providerName = Validate.notBlank(providerName, "Provider must not be blank");
    systemPrompt = Validate.notBlank(systemPrompt, "System prompt must not be blank");
    userInput = Validate.notBlank(userInput, "User input must not be blank");
    Validate.isTrue(maxOutputTokens == null || maxOutputTokens > 0,
        "maxOutputTokens must be greater than 0 when set");
  }

  /**
   * Creates a request that omits an explicit model so the client uses its configured default.
   *
   * @param providerName target provider id
   * @param systemPrompt system instructions
   * @param userInput    user content for this turn
   * @return request with {@code model == null}
   */
  public static LlmExecutionRequest withDefaultModel(String providerName, String systemPrompt,
      String userInput) {
    return new LlmExecutionRequest(providerName, null, systemPrompt, userInput);
  }
}
