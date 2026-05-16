package com.agentforge4j.llm;

import java.util.Optional;
import org.apache.commons.lang3.Strings;

/**
 * Executes LLM requests for a single registered provider (for example OpenAI, Ollama, or Claude).
 * <p>
 * Implementations are instantiated by {@link LlmClientFactory} and managed by
 * {@link LlmClientResolver}. Each client instance is bound to one provider id returned by
 * {@link #getProviderName()}.
 */
public interface LlmClient {

  /**
   * Returns the provider id this client executes against (lowercase, such as {@code "openai"}).
   *
   * @return non-blank provider id
   */
  String getProviderName();

  /**
   * Executes an LLM request and returns the response.
   *
   * @param request provider id, prompts, and optional model override for this call
   * @return provider response body, typically JSON text for downstream parsing
   * @throws LlmInvocationException if the request fails due to network issues, invalid responses,
   *                                or provider-specific errors
   */
  String execute(LlmExecutionRequest request);

  default Optional<LlmRetryPolicy> getRetryPolicy() {
    return Optional.empty();
  }

  /**
   * Removes markdown code fence markers from the input if present.
   * <p>
   * Strips leading {@code ```} followed by an optional language identifier, and trailing
   * {@code ```}. Returns the input unchanged if it does not start with backticks.
   *
   * @param input the potentially fence-marked string
   * @return the input with fences removed, or the input unchanged
   */
  static String stripCodeFence(String input) {
    if (input == null) {
      return null;
    }
    if (!Strings.CS.startsWith(input, "```")) {
      return input;
    }
    int firstNewline = input.indexOf('\n');
    if (firstNewline < 0) {
      return input;
    }
    String afterOpeningFence = input.substring(firstNewline + 1);
    int closingFence = afterOpeningFence.lastIndexOf("```");
    if (closingFence < 0) {
      return afterOpeningFence;
    }
    return afterOpeningFence.substring(0, closingFence).strip();
  }
}
