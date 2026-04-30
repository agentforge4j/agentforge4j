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
 */
public record LlmExecutionRequest(
    String providerName,
    String model,
    String systemPrompt,
    String userInput) {

  /**
   *  Validates that required fields are not blank.
   *
   * @throws IllegalArgumentException if any required field is blank
   */
  public LlmExecutionRequest {
    providerName = Validate.notBlank(providerName, "Provider must not be blank");
    systemPrompt = Validate.notBlank(systemPrompt, "System prompt must not be blank");
    userInput = Validate.notBlank(userInput, "User input must not be blank");
  }

  /**
   * Creates an execution request using the providerName's default model.
   *
   * @param providerName the providerName name
   * @param systemPrompt the system prompt
   * @param userInput    the user input
   * @return a request with no specific model specified
   */
  public static LlmExecutionRequest withDefaultModel(String providerName, String systemPrompt,
      String userInput) {
    return new LlmExecutionRequest(providerName, null, systemPrompt, userInput);
  }
}
