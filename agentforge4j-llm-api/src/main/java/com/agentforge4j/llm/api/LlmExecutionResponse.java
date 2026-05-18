package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;

/**
 * Result of a single {@link LlmClient} invocation.
 *
 * @param text       model output text; never {@code null}
 * @param tokenUsage provider-reported token counts, or {@code null} when the provider returned no
 *                   usage data (absence convention — not an empty populated record)
 */
public record LlmExecutionResponse(String text, TokenUsageReport tokenUsage) {

  /**
   * Validates that {@code text} is non-null. {@code tokenUsage} may be {@code null}.
   *
   * @throws IllegalArgumentException if {@code text} is {@code null}
   */
  public LlmExecutionResponse {
    text = Validate.notNull(text, "text must not be null");
  }
}
