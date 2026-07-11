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
 * emits exactly the 8 fields the estimator design names:
 * {@code recommendation, confidence, complexity, riskFlags, minimumRequiredTokens,
 * estimatedMinTokens, estimatedExpectedTokens, estimatedMaxTokens}. Excludes {@code workflowId},
 * {@code estimatedAgentTurns}, {@code estimatedToolInvocations}, and {@code estimatedSteps} by
 * design.
 *
 * <p>{@code riskFlags} is carried through unmodified from whichever analyzer produced the
 * structural summary (never re-derived here): {@code WorkflowComplexityAnalyzer} (Mode 1) and
 * {@code EpicPackageComplexityAnalyzer} (Mode 2) raise the same flags from different, mode-specific
 * thresholds, so a generic re-derivation from the other structural fields would misclassify one mode
 * or the other.
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

    WorkflowComplexityAnalysis analysis = new WorkflowComplexityAnalysis(
        AGGREGATOR_ID,
        asInt(values, "stepCount"),
        0,
        0,
        0,
        0,
        0,
        0,
        asLong(values, "minAgentTurns"),
        asLong(values, "expectedAgentTurns"),
        asLong(values, "maxAgentTurns"),
        0,
        true,
        null,
        asLong(values, "minimumRequiredTokens"),
        asComplexityClass(values, "complexity"),
        asRiskFlags(values, "riskFlags"));

    SizingInputs sizing = new SizingInputs(
        asInt(values, "estimatedInputTokensPerAgentTurn"),
        asInt(values, "estimatedOutputTokensPerAgentTurn"),
        asInt(values, "estimatedToolInvocationsPerAgentTurn"));

    ExecutionEstimate estimate = WorkflowExecutionAggregator.aggregate(analysis, sizing);

    return Map.ofEntries(
        Map.entry("recommendation", stringValue(estimate.recommendation().name())),
        Map.entry("confidence", stringValue(estimate.confidence().name())),
        Map.entry("complexity", stringValue(estimate.complexity().name())),
        Map.entry("riskFlags", riskFlagsValue(estimate.riskFlags())),
        Map.entry("minimumRequiredTokens", numberValue(estimate.minimumRequiredTokens())),
        Map.entry("estimatedMinTokens", numberValue(estimate.estimatedMinTokens())),
        Map.entry("estimatedExpectedTokens", numberValue(estimate.estimatedExpectedTokens())),
        Map.entry("estimatedMaxTokens", numberValue(estimate.estimatedMaxTokens())));
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

  private static ContextValue require(Map<String, ContextValue> values, String key) {
    ContextValue value = values.get(key);
    Validate.notNull(value, "Missing declared context value for key '%s'".formatted(key));
    return value;
  }

  private static long asLong(Map<String, ContextValue> values, String key) {
    ContextValue value = require(values, key);
    if (value instanceof NumberContextValue number) {
      double raw = number.value().doubleValue();
      if (Double.isNaN(raw) || Double.isInfinite(raw) || raw != Math.rint(raw)) {
        throw new IllegalArgumentException(
            "Context value for key '%s' must be an exact integer but was %s"
                .formatted(key, number.value()));
      }
      return number.value().longValue();
    }
    if (value instanceof StringContextValue string) {
      try {
        return Long.parseLong(string.value());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Context value for key '%s' is not a valid integer: '%s'".formatted(key, string.value()), e);
      }
    }
    throw new IllegalArgumentException(
        "Context value for key '%s' must be numeric or a numeric string but was %s"
            .formatted(key, value.getClass().getSimpleName()));
  }

  private static int asInt(Map<String, ContextValue> values, String key) {
    long raw = asLong(values, key);
    if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "Context value for key '%s' must fit in a 32-bit integer but was %d".formatted(key, raw));
    }
    return (int) raw;
  }

  private static ComplexityClass asComplexityClass(Map<String, ContextValue> values, String key) {
    ContextValue value = require(values, key);
    if (value instanceof StringContextValue string) {
      return ComplexityClass.valueOf(string.value());
    }
    throw new IllegalArgumentException(
        "Context value for key '%s' must be a string but was %s"
            .formatted(key, value.getClass().getSimpleName()));
  }

  private static List<RiskFlag> asRiskFlags(Map<String, ContextValue> values, String key) {
    ContextValue value = require(values, key);
    if (!(value instanceof StringContextValue string)) {
      throw new IllegalArgumentException(
          "Context value for key '%s' must be a string but was %s"
              .formatted(key, value.getClass().getSimpleName()));
    }
    if (NO_RISK_FLAGS.equals(string.value())) {
      return List.of();
    }
    List<RiskFlag> flags = new ArrayList<>();
    for (String token : string.value().split(",")) {
      flags.add(RiskFlag.valueOf(token.trim()));
    }
    return List.copyOf(flags);
  }
}
