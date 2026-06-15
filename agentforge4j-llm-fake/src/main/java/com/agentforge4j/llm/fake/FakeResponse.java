package com.agentforge4j.llm.fake;

import com.agentforge4j.util.Validate;

/**
 * A single scripted response: the raw model text the fake provider returns, and optional script-specified token usage.
 * When {@code tokenUsage} is {@code null}, the client derives a deterministic length-based usage instead.
 *
 * @param responseText raw model output text; must not be {@code null} (may be empty), mirroring
 *                     {@link com.agentforge4j.llm.api.LlmExecutionResponse#text()}
 * @param tokenUsage   script-specified token usage, or {@code null} to use the deterministic fallback
 */
public record FakeResponse(String responseText, FakeTokenUsage tokenUsage) {

  /**
   * Validates that {@code responseText} is non-null.
   *
   * @throws IllegalArgumentException if {@code responseText} is {@code null}
   */
  public FakeResponse {
    Validate.notNull(responseText, "responseText must not be null");
  }
}
