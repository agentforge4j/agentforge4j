// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.util.Validate;

/**
 * {@link CompactionMode} that compacts a large, messy natural-language source by summarizing it
 * through the normal agent-invocation path at a declared capability tier (full audit applies).
 *
 * <p>{@code agentRef} names the workflow-configured agent the summarization runs through — the same
 * mechanism as any {@code AGENT} step invocation, with no runtime special-casing. The workflow author
 * is responsible for authoring and wiring that agent (typically bundle-local; see doc 04's reuse
 * litmus test for when to promote it to {@code shipped-agents/}).
 *
 * @param modelTier the capability tier name the summarization runs at; non-blank. Stored as the tier
 *                  name (a String) so {@code core} stays free of the {@code llm-api} enum; the name is
 *                  validated at the runtime invocation boundary
 * @param agentRef  non-blank reference to the agent definition the summarization is invoked through;
 *                  resolved against the same agent repository as {@code AGENT}/{@code SPAR} steps
 */
public record LlmSummary(String modelTier, String agentRef) implements CompactionMode {

  public LlmSummary {
    Validate.notBlank(modelTier, "LlmSummary modelTier must not be blank");
    Validate.notBlank(agentRef, "LlmSummary agentRef must not be blank");
  }
}
