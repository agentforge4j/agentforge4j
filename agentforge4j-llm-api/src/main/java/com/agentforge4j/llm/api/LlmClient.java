// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.api;

/**
 * Executes LLM requests for a single registered provider (for example OpenAI, Ollama, or Claude).
 * <p>
 * Implementations are typically instantiated and managed by a discovery mechanism provided by a
 * consuming module. Each client instance is bound to one provider id returned by
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
   * @return execution response containing model output and optional token usage
   * @throws LlmInvocationException if the request fails due to network issues, invalid responses,
   *                                or provider-specific errors
   */
  LlmExecutionResponse execute(LlmExecutionRequest request);

  /**
   * The retry policy this client requests, or {@code null} if it has none. Callers that need a
   * policy either way should fall back to {@link LlmRetryPolicy#defaults()} on a {@code null}
   * return.
   *
   * @return the requested retry policy, or {@code null} when this client has none
   */
  default LlmRetryPolicy getRetryPolicy() {
    return null;
  }
}
