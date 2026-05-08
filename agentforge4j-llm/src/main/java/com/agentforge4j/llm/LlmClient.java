package com.agentforge4j.llm;

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
}
