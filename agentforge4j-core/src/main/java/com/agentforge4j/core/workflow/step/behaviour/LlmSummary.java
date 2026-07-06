// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.util.Validate;

/**
 * {@link CompactionMode} that compacts a large, messy natural-language source by summarizing it
 * through the normal agent-invocation path at a declared capability tier (full audit applies).
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
