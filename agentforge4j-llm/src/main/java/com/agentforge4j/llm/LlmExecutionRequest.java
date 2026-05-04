package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

/**
 * An immutable request to execute an LLM operation.
 *
 * @param providerName     the providerName name such as {@code "openai"} or {@code "ollama"}
 * @param model        the specific model identifier for this request, or {@code null} to use the
 *                     providerName's default
 * @param systemPrompt the system prompt establishing the LLM's behavior and context
 * @param userInput    the user's query or instruction for the LLM
 * @param maxOutputTokens optional cap on generated tokens (provider-specific; ignored when null)
 */
public record LlmExecutionRequest(
    String providerName,
    String model,
    String systemPrompt,
    String userInput,
    Integer maxOutputTokens) {

  public LlmExecutionRequest(String providerName, String model, String systemPrompt, String userInput) {
    this(providerName, model, systemPrompt, userInput, null);
  }

  /**
   *  Validates that required fields are not blank.
   *
   * @throws IllegalArgumentException if any required field is blank
   */
  public LlmExecutionRequest {
    providerName = Validate.notBlank(providerName, "Provider must not be blank");
    systemPrompt = Validate.notBlank(systemPrompt, "System prompt must not be blank");
    userInput = Validate.notBlank(userInput, "User input must not be blank");
    Validate.isTrue(maxOutputTokens == null || maxOutputTokens > 0,
        "maxOutputTokens must be positive when set");
  }

  /**
   * Creates an execution request using the providerName's default model.
   *
   * @param providerName the providerName
   * @param systemPrompt the system prompt
   * @param userInput    the user input
   * @return a request with no specific model specified
   */
  public static LlmExecutionRequest withDefaultModel(String providerName, String systemPrompt,
      String userInput) {
    return new LlmExecutionRequest(providerName, null, systemPrompt, userInput);
  }
}
