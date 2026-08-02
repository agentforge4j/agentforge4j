// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.core.spi.aggregation.AggregationContext;
import com.agentforge4j.core.spi.aggregation.ContextAggregator;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Built-in {@link ContextAggregator} wrapping {@link WorkflowExecutionAggregator#aggregate} for
 * in-workflow use by the {@code workflow-execution-estimator} catalog bundle's {@code AGGREGATE}
 * step. Reconstructs the {@link WorkflowComplexityAnalysis} and {@link SizingInputs} the aggregator
 * needs from the step's declared context values — the {@code structural-summary} artifact fields
 * always arrive as {@link StringContextValue} (submitted as {@code INPUT} answers), while the
 * {@code execution-estimator} agent's sizing figures arrive as {@link NumberContextValue} — and
 * emits the 8 fields the estimator design names ({@code recommendation, confidence, complexity,
 * riskFlags, minimumRequiredTokens, estimatedMinTokens, estimatedExpectedTokens,
 * estimatedMaxTokens}) plus {@code iterationCeiling}, the submitted structural-summary value passed
 * through unmodified for disclosure alongside the risk flags it may have contributed to (it is
 * never itself part of {@link WorkflowExecutionAggregator#aggregate}'s token-envelope math).
 * Excludes {@code workflowId}, {@code estimatedAgentTurns}, {@code estimatedToolInvocations}, and
 * {@code estimatedSteps} by design.
 *
 * <p>{@code riskFlags} is carried through unmodified from whichever analyzer produced the
 * structural summary (never re-derived here): {@code WorkflowComplexityAnalyzer} (Mode 1) and
 * {@code EpicPackageComplexityAnalyzer} (Mode 2) raise the same flags from different, mode-specific
 * thresholds, so a generic re-derivation from the other structural fields would misclassify one mode
 * or the other. {@link RiskFlag#WIDE_TOKEN_ENVELOPE} is the sole exception: it is only ever
 * computed post-sizing by {@link WorkflowExecutionAggregator#aggregate}, so no structural analyzer
 * can legitimately submit it here — a submitted {@code WIDE_TOKEN_ENVELOPE} is rejected fail-closed
 * rather than silently passed through.
 *
 * <p>{@code WorkflowComplexityAnalysis.workflowId()} is required non-blank by that record's
 * constructor but is excluded from this aggregator's output and not exposed by
 * {@link AggregationContext} (which, like {@code ArtifactValidationContext}, carries no run/workflow
 * identity) — this aggregator's own {@link #aggregatorId()} is used as a stable, non-blank
 * placeholder, never asserted anywhere in the emitted output.
 */
public final class WorkflowExecutionEstimateAggregator implements ContextAggregator {

  private static final String AGGREGATOR_ID = "workflow-execution-estimate";
  private static final String NO_RISK_FLAGS = "NONE";

  @Override
  public String aggregatorId() {
    return AGGREGATOR_ID;
  }

  @Override
  public Map<String, ContextValue> aggregate(AggregationContext context) {
    Validate.notNull(context, "context must not be null");
    Map<String, ContextValue> values = context.values();
    long iterationCeiling = ContextValueNumbers.asLong(values, "iterationCeiling");

    WorkflowComplexityAnalysis analysis = new WorkflowComplexityAnalysis(
        AGGREGATOR_ID,
        ContextValueNumbers.asInt(values, "stepCount"),
        0,
        0,
        0,
        0,
        0,
        0,
        ContextValueNumbers.asLong(values, "minAgentTurns"),
        ContextValueNumbers.asLong(values, "expectedAgentTurns"),
        ContextValueNumbers.asLong(values, "maxAgentTurns"),
        iterationCeiling,
        true,
        null,
        ContextValueNumbers.asLong(values, "minimumRequiredTokens"),
        asComplexityClass(values, "complexity"),
        asRiskFlags(values, "riskFlags"));

    SizingInputs sizing = new SizingInputs(
        ContextValueNumbers.asInt(values, "estimatedInputTokensPerAgentTurn"),
        ContextValueNumbers.asInt(values, "estimatedOutputTokensPerAgentTurn"),
        ContextValueNumbers.asInt(values, "estimatedToolInvocationsPerAgentTurn"));

    ExecutionEstimate estimate = WorkflowExecutionAggregator.aggregate(analysis, sizing);

    return Map.ofEntries(
        Map.entry("recommendation", stringValue(estimate.recommendation().name())),
        Map.entry("confidence", stringValue(estimate.confidence().name())),
        Map.entry("complexity", stringValue(estimate.complexity().name())),
        Map.entry("riskFlags", riskFlagsValue(estimate.riskFlags())),
        Map.entry("minimumRequiredTokens", numberValue(estimate.minimumRequiredTokens())),
        Map.entry("estimatedMinTokens", numberValue(estimate.estimatedMinTokens())),
        Map.entry("estimatedExpectedTokens", numberValue(estimate.estimatedExpectedTokens())),
        Map.entry("estimatedMaxTokens", numberValue(estimate.estimatedMaxTokens())),
        Map.entry("iterationCeiling", numberValue(iterationCeiling)));
  }

  private static ContextValue stringValue(String value) {
    return new StringContextValue(value, ContextProvenance.SYSTEM_GENERATED);
  }

  private static ContextValue numberValue(long value) {
    return new NumberContextValue(value, ContextProvenance.SYSTEM_GENERATED);
  }

  private static ContextValue riskFlagsValue(List<RiskFlag> riskFlags) {
    List<ContextValue> entries = riskFlags.stream()
        .map(flag -> (ContextValue) stringValue(flag.name()))
        .toList();
    return new ContextValueList(entries, ContextProvenance.SYSTEM_GENERATED);
  }

  private static StringContextValue requireString(Map<String, ContextValue> values, String key) {
    ContextValue value = ContextValueNumbers.require(values, key);
    if (value instanceof StringContextValue string) {
      return string;
    }
    throw new IllegalArgumentException(
        "Context value for key '%s' must be a string but was %s"
            .formatted(key, value.getClass().getSimpleName()));
  }

  private static ComplexityClass asComplexityClass(Map<String, ContextValue> values, String key) {
    StringContextValue string = requireString(values, key);
    try {
      return ComplexityClass.valueOf(string.value());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Context value for key '%s' is not a valid complexity class: '%s'"
              .formatted(key, string.value()), e);
    }
  }

  private static List<RiskFlag> asRiskFlags(Map<String, ContextValue> values, String key) {
    StringContextValue string = requireString(values, key);
    if (NO_RISK_FLAGS.equals(string.value())) {
      return List.of();
    }
    List<RiskFlag> flags = new ArrayList<>();
    for (String token : string.value().split(",", -1)) {
      String trimmed = token.trim();
      RiskFlag flag;
      try {
        flag = RiskFlag.valueOf(trimmed);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Context value for key '%s' contains an unknown risk flag: '%s'"
                .formatted(key, trimmed), e);
      }
      if (flag == RiskFlag.WIDE_TOKEN_ENVELOPE) {
        throw new IllegalArgumentException(
            ("Context value for key '%s' must not submit WIDE_TOKEN_ENVELOPE: it is only ever "
                + "computed post-sizing from the aggregated token range, never a structural input")
                    .formatted(key));
      }
      flags.add(flag);
    }
    return List.copyOf(flags);
  }
}
