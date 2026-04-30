package com.agentforge4j.llm;

/**
 * Executes LLM requests against a specific providerName.
 * <p>
 * Implementations are instantiated by {@link LlmClientFactory} and managed by
 * {@link LlmClientResolver}. Each implementation provides a single providerName (e.g., OpenAI, Ollama,
 * Claude).
 */
public interface LlmClient {

  /**
   * Returns the providerName name this client executes against.
   *
   * @return providerName name such as {@code "openai"} or {@code "ollama"}
   */
  String getProviderName();

  /**
   * Executes an LLM request and returns the response.
   *
   * @param request the LLM execution request with providerName, model, system prompt, and user input
   * @return the response from the LLM providerName, typically a JSON string
   * @throws LlmInvocationException if the request fails due to network issues, invalid responses,
   *                                or providerName-specific errors
   */
  String execute(LlmExecutionRequest request);
}
