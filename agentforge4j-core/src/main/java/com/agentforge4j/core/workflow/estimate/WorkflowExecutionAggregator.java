// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.util.Validate;
import java.util.EnumSet;
import java.util.List;

/**
 * Combines a deterministic {@link WorkflowComplexityAnalysis} with the LLM-produced per-turn
 * {@link SizingInputs} to yield the final {@link ExecutionEstimate}: the min / expected / max token
 * envelope, the sized turn and tool figures, the confidence grade, the risk flags, and the
 * continue/narrow/stop recommendation. Pure and deterministic — no I/O, no run state, no money.
 *
 * <p>The token envelope is the deterministic input floor plus the loop-aware agent-turn attribution
 * multiplied by the per-turn token magnitudes:
 * {@code tokens = minimumRequiredTokens + agentTurns * (inputPerTurn + outputPerTurn)}, evaluated at
 * the minimum, expected, and maximum turn counts respectively.
 *
 * <p>The confidence mapping and the wide-envelope ratio are deterministic implementation choices,
 * intended to be tunable; they are not a published contract.
 */
public final class WorkflowExecutionAggregator {

  /** Max-to-min token ratio at or above which {@link RiskFlag#WIDE_TOKEN_ENVELOPE} is raised. */
  private static final long WIDE_ENVELOPE_RATIO = 4L;

  /**
   * Saturating cap for token/turn products and sums, mirroring {@code WorkflowComplexityAnalyzer}'s
   * {@code saturatingMultiply}, so an extreme submitted turn count cannot silently overflow a long
   * into a misleadingly small or negative disclosed figure. A quarter of the {@code long} range
   * leaves headroom for every addition/multiplication chain below to saturate without itself
   * re-overflowing.
   */
  private static final long SATURATION_CAP = Long.MAX_VALUE / 4;

  private WorkflowExecutionAggregator() {
  }

  /**
   * Aggregates a structural analysis and the per-turn sizing into a final execution estimate.
   *
   * @param analysis the deterministic structural analysis; must not be {@code null}
   * @param sizing   the per-turn sizing magnitudes; must not be {@code null}
   *
   * @return the neutral execution estimate; never {@code null}
   */
  public static ExecutionEstimate aggregate(WorkflowComplexityAnalysis analysis,
      SizingInputs sizing) {
    Validate.notNull(analysis, "analysis must not be null");
    Validate.notNull(sizing, "sizing must not be null");

    long perTurnTokens = (long) sizing.inputTokensPerAgentTurn() + sizing.outputTokensPerAgentTurn();
    long minTokens = saturatingAdd(analysis.minimumRequiredTokens(),
        saturatingMultiply(analysis.minAgentTurns(), perTurnTokens));
    long expectedTokens = saturatingAdd(analysis.minimumRequiredTokens(),
        saturatingMultiply(analysis.expectedAgentTurns(), perTurnTokens));
    long maxTokens = saturatingAdd(analysis.minimumRequiredTokens(),
        saturatingMultiply(analysis.maxAgentTurns(), perTurnTokens));

    long estimatedToolInvocations =
        saturatingMultiply(analysis.expectedAgentTurns(), sizing.toolInvocationsPerAgentTurn());

    boolean wideEnvelope = minTokens > 0 && maxTokens >= saturatingMultiply(minTokens, WIDE_ENVELOPE_RATIO);

    EnumSet<RiskFlag> flags = EnumSet.noneOf(RiskFlag.class);
    flags.addAll(analysis.riskFlags());
    if (wideEnvelope) {
      flags.add(RiskFlag.WIDE_TOKEN_ENVELOPE);
    }

    ConfidenceGrade confidence = confidence(analysis.complexityClass(), wideEnvelope);
    Recommendation recommendation = recommend(analysis.complexityClass());

    return new ExecutionEstimate(
        analysis.workflowId(),
        analysis.complexityClass(),
        confidence,
        minTokens,
        expectedTokens,
        maxTokens,
        analysis.minimumRequiredTokens(),
        analysis.expectedAgentTurns(),
        estimatedToolInvocations,
        analysis.stepCount(),
        List.copyOf(flags),
        recommendation);
  }

  private static ConfidenceGrade confidence(ComplexityClass complexity, boolean wideEnvelope) {
    return switch (complexity) {
      case SIMPLE -> ConfidenceGrade.HIGH;
      case MODERATE -> wideEnvelope ? ConfidenceGrade.MEDIUM : ConfidenceGrade.HIGH;
      case COMPLEX -> wideEnvelope ? ConfidenceGrade.LOW : ConfidenceGrade.MEDIUM;
      case HIGH_RISK -> wideEnvelope ? ConfidenceGrade.VERY_LOW : ConfidenceGrade.LOW;
    };
  }

  private static Recommendation recommend(ComplexityClass complexity) {
    return complexity == ComplexityClass.HIGH_RISK ? Recommendation.NARROW : Recommendation.CONTINUE;
  }

  private static long saturatingMultiply(long a, long b) {
    long product = a * b;
    if (b != 0 && (product / b != a || product > SATURATION_CAP)) {
      return SATURATION_CAP;
    }
    return Math.min(product, SATURATION_CAP);
  }

  private static long saturatingAdd(long a, long b) {
    long sum = a + b;
    if (sum < a || sum > SATURATION_CAP) {
      return SATURATION_CAP;
    }
    return sum;
  }
}
