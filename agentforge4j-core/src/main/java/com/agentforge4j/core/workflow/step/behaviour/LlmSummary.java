// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.util.Validate;

/**
 * {@link CompactionMode} that compacts a large, messy natural-language source by summarizing it
 * through the normal agent-invocation path at a declared capability tier (full audit applies).
 *
 * <p><strong>Not yet invoked by the shipped runtime:</strong> this shape carries a tier but no agent
 * identity, so there is no agent to invoke through the normal invocation path; reaching an
 * {@code LLM_SUMMARY} step fails with a clear error naming the gap rather than fabricating a
 * summary. The declaration exists so workflow definitions remain stable once the invocation
 * convention is decided.
 *
 * @param modelTier the capability tier name the summarization runs at; non-blank. Stored as the tier
 *                  name (a String) so {@code core} stays free of the {@code llm-api} enum; the name is
 *                  validated at the runtime invocation boundary
 */
public record LlmSummary(String modelTier) implements CompactionMode {

  public LlmSummary {
    Validate.notBlank(modelTier, "LlmSummary modelTier must not be blank");
  }
}
