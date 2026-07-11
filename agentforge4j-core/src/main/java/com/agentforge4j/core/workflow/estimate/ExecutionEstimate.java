// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * The neutral execution-estimate report produced by {@code WorkflowExecutionAggregator}: the token
 * envelope, the deterministic minimum input floor, the sized turn / tool / step figures, the
 * complexity and confidence, the structural risk flags, and the continue/narrow/stop recommendation.
 * It is execution shape only — it carries no currency, pricing, billing, or credit concept. An
 * embedding application may map it to other domains strictly downstream.
 *
 * @param workflowId              non-blank id of the estimated workflow
 * @param complexity              deterministic complexity classification
 * @param confidence              confidence grade for the estimate
 * @param estimatedMinTokens      lower bound of the total-token envelope
 * @param estimatedExpectedTokens expected-case total tokens
 * @param estimatedMaxTokens      upper bound of the total-token envelope
 * @param minimumRequiredTokens   deterministic unavoidable input-token floor to start
 * @param estimatedAgentTurns     expected-case agent turns
 * @param estimatedToolInvocations expected-case tool invocations
 * @param estimatedSteps          reachable step count (not loop-expanded)
 * @param riskFlags               immutable, de-duplicated risk flags
 * @param recommendation          neutral continue/narrow/stop recommendation
 */
public record ExecutionEstimate(
    String workflowId,
    ComplexityClass complexity,
    ConfidenceGrade confidence,
    long estimatedMinTokens,
    long estimatedExpectedTokens,
    long estimatedMaxTokens,
    long minimumRequiredTokens,
    long estimatedAgentTurns,
    long estimatedToolInvocations,
    int estimatedSteps,
    List<RiskFlag> riskFlags,
    Recommendation recommendation
) {

  public ExecutionEstimate {
    Validate.notBlank(workflowId, "workflowId must not be blank");
    Validate.notNull(complexity, "complexity must not be null");
    Validate.notNull(confidence, "confidence must not be null");
    Validate.isNotNegative(estimatedMinTokens, "estimatedMinTokens must not be negative");
    Validate.isNotNegative(estimatedExpectedTokens, "estimatedExpectedTokens must not be negative");
    Validate.isNotNegative(estimatedMaxTokens, "estimatedMaxTokens must not be negative");
    Validate.isNotNegative(minimumRequiredTokens, "minimumRequiredTokens must not be negative");
    Validate.isNotNegative(estimatedAgentTurns, "estimatedAgentTurns must not be negative");
    Validate.isNotNegative(estimatedToolInvocations, "estimatedToolInvocations must not be negative");
    Validate.isNotNegative(estimatedSteps, "estimatedSteps must not be negative");
    Validate.isTrue(estimatedMinTokens <= estimatedExpectedTokens,
        "estimatedMinTokens (%s) must not exceed estimatedExpectedTokens (%s)"
            .formatted(estimatedMinTokens, estimatedExpectedTokens));
    Validate.isTrue(estimatedExpectedTokens <= estimatedMaxTokens,
        "estimatedExpectedTokens (%s) must not exceed estimatedMaxTokens (%s)"
            .formatted(estimatedExpectedTokens, estimatedMaxTokens));
    Validate.notNull(recommendation, "recommendation must not be null");
    riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
  }
}
