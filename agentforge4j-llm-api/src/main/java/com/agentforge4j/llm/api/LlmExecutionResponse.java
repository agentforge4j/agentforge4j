package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;

/**
 * Result of a single {@link LlmClient} invocation.
 *
 * @param text       model output text; never {@code null}
 * @param modelUsed  concrete model the provider actually ran for this call; may differ from
 *                   {@link LlmExecutionRequest#model()} when the caller passed {@code null}, an
 *                   alias, or the provider performed server-side routing; {@code null} when the
 *                   provider response does not include a model field (Bedrock passes through the
 *                   invoked {@code modelId} because the Anthropic InvokeModel body does not carry
 *                   it)
 * @param tokenUsage provider-reported token counts, or {@code null} when the provider returned no
 *                   usage block at all for this call (absence convention — not an empty populated
 *                   record)
 */
public record LlmExecutionResponse(String text, String modelUsed, TokenUsageReport tokenUsage) {

  /**
   * Validates that {@code text} is non-null. {@code modelUsed} and {@code tokenUsage} may be
   * {@code null}.
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}
   */
  public LlmExecutionResponse {
    text = Validate.notNull(text, "text must not be null");
  }
}
