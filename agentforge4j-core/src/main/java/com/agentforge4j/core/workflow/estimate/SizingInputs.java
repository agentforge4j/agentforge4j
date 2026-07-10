// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.util.Validate;

/**
 * Per-unit sizing magnitudes for the genuinely dynamic parts of an estimate — the figures the
 * {@code execution-estimator} agent produces over a structural summary and the host reads back to
 * feed {@code WorkflowExecutionAggregator}. They describe execution shape (tokens, tool calls) per
 * model turn only — never money or provider pricing.
 *
 * @param inputTokensPerAgentTurn    expected input tokens per agent turn; must not be negative
 * @param outputTokensPerAgentTurn   expected output tokens per agent turn; must not be negative
 * @param toolInvocationsPerAgentTurn expected tool invocations per agent turn; must not be negative
 */
public record SizingInputs(
    int inputTokensPerAgentTurn,
    int outputTokensPerAgentTurn,
    int toolInvocationsPerAgentTurn
) {

  public SizingInputs {
    Validate.isNotNegative(inputTokensPerAgentTurn, "inputTokensPerAgentTurn must not be negative");
    Validate.isNotNegative(outputTokensPerAgentTurn, "outputTokensPerAgentTurn must not be negative");
    Validate.isNotNegative(toolInvocationsPerAgentTurn, "toolInvocationsPerAgentTurn must not be negative");
  }
}
