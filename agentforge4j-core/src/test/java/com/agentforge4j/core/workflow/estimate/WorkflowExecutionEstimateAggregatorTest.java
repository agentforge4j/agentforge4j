// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the built-in {@code workflow-execution-estimate} {@link ContextAggregator}'s type
 * conversion: {@code structural-summary} fields always arrive as {@link StringContextValue}
 * (submitted as {@code INPUT} answers) while the {@code execution-estimator} agent's sizing
 * figures arrive as {@link NumberContextValue} — both must be read correctly, and the result must
 * match {@link WorkflowExecutionAggregator#aggregate} called directly with the equivalent typed
 * arguments.
 */
class WorkflowExecutionEstimateAggregatorTest {

  private static final WorkflowExecutionEstimateAggregator AGGREGATOR =
      new WorkflowExecutionEstimateAggregator();

  private static ContextValue string(String value) {
    return new StringContextValue(value, com.agentforge4j.core.workflow.context.ContextProvenance.USER_SUPPLIED);
  }

  private static ContextValue number(long value) {
    return new NumberContextValue(value, com.agentforge4j.core.workflow.context.ContextProvenance.LLM_GENERATED);
  }

  private static Map<String, ContextValue> declaredValues(String riskFlags) {
    Map<String, ContextValue> values = new LinkedHashMap<>();
    values.put("complexity", string("HIGH_RISK"));
    values.put("stepCount", string("58"));
    values.put("minimumRequiredTokens", string("10500"));
    values.put("minAgentTurns", string("52"));
    values.put("expectedAgentTurns", string("88"));
    values.put("maxAgentTurns", string("220"));
    values.put("riskFlags", string(riskFlags));
    values.put("estimatedInputTokensPerAgentTurn", number(900));
    values.put("estimatedOutputTokensPerAgentTurn", number(500));
    values.put("estimatedToolInvocationsPerAgentTurn", number(1));
    return values;
  }

  @Test
  void aggregatorIdIsStable() {
    assertThat(AGGREGATOR.aggregatorId()).isEqualTo("workflow-execution-estimate");
  }

  @Test
  void matchesWorkflowExecutionAggregatorForEquivalentTypedArguments() {
    Map<String, ContextValue> declared =
        declaredValues("AGENT_DRIVEN_LOOP,LLM_DECIDED_BRANCHING,LARGE_STRUCTURE");

    Map<String, ContextValue> result = AGGREGATOR.aggregate(() -> declared);

    WorkflowComplexityAnalysis analysis = new WorkflowComplexityAnalysis(
        "workflow-execution-estimate", 58, 0, 0, 0, 0, 0, 0, 52, 88, 220, 0, true, null, 10500,
        ComplexityClass.HIGH_RISK,
        List.of(RiskFlag.AGENT_DRIVEN_LOOP, RiskFlag.LLM_DECIDED_BRANCHING, RiskFlag.LARGE_STRUCTURE));
    SizingInputs sizing = new SizingInputs(900, 500, 1);
    ExecutionEstimate expected = WorkflowExecutionAggregator.aggregate(analysis, sizing);

    assertThat(result).hasSize(8);
    assertThat(stringValue(result, "recommendation")).isEqualTo(expected.recommendation().name());
    assertThat(stringValue(result, "confidence")).isEqualTo(expected.confidence().name());
    assertThat(stringValue(result, "complexity")).isEqualTo(expected.complexity().name());
    assertThat(numberValue(result, "minimumRequiredTokens")).isEqualTo(expected.minimumRequiredTokens());
    assertThat(numberValue(result, "estimatedMinTokens")).isEqualTo(expected.estimatedMinTokens());
    assertThat(numberValue(result, "estimatedExpectedTokens")).isEqualTo(expected.estimatedExpectedTokens());
    assertThat(numberValue(result, "estimatedMaxTokens")).isEqualTo(expected.estimatedMaxTokens());
    assertThat(riskFlagNames(result)).containsExactlyElementsOf(
        expected.riskFlags().stream().map(RiskFlag::name).toList());
  }

  @Test
  void noneSentinelProducesEmptyRiskFlagsList() {
    Map<String, ContextValue> declared = declaredValues("NONE");

    Map<String, ContextValue> result = AGGREGATOR.aggregate(() -> declared);

    ContextValue riskFlags = result.get("riskFlags");
    assertThat(riskFlags).isInstanceOf(ContextValueList.class);
    assertThat(((ContextValueList) riskFlags).values()).isEmpty();
  }

  @Test
  void riskFlagsCarriedThroughUnmodifiedNeverReDerived() {
    // A Mode-2 shape (epic-package) where the generic step-count threshold (>= 20) would spuriously
    // raise LARGE_STRUCTURE if re-derived, but the analyzer that actually produced this structural
    // summary did not raise it (a 5-epic package, below EpicPackageComplexityAnalyzer's own
    // epic-count threshold) — proving the flags are read verbatim, never recomputed here.
    Map<String, ContextValue> declared = declaredValues("AGENT_DRIVEN_LOOP");

    Map<String, ContextValue> result = AGGREGATOR.aggregate(() -> declared);

    assertThat(riskFlagNames(result)).containsExactly("AGENT_DRIVEN_LOOP");
  }

  @Test
  void missingDeclaredKeyFailsClosed() {
    Map<String, ContextValue> declared = new LinkedHashMap<>(declaredValues("NONE"));
    declared.remove("minAgentTurns");

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minAgentTurns");
  }

  @Test
  void malformedNumericStringFailsClosedWithKeyAndValue() {
    Map<String, ContextValue> declared = new LinkedHashMap<>(declaredValues("NONE"));
    declared.put("stepCount", string("not-a-number"));

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stepCount")
        .hasMessageContaining("not-a-number");
  }

  @Test
  void unknownRiskFlagNameFailsClosedWithKeyAndValue() {
    Map<String, ContextValue> declared = declaredValues("NOT_A_REAL_FLAG");

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("riskFlags")
        .hasMessageContaining("NOT_A_REAL_FLAG");
  }

  @Test
  void unknownComplexityClassFailsClosedWithKeyAndValue() {
    Map<String, ContextValue> declared = new LinkedHashMap<>(declaredValues("NONE"));
    declared.put("complexity", string("NOT_A_REAL_CLASS"));

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("complexity")
        .hasMessageContaining("NOT_A_REAL_CLASS");
  }

  @Test
  void submittedWideTokenEnvelopeRiskFlagFailsClosed() {
    Map<String, ContextValue> declared = declaredValues("WIDE_TOKEN_ENVELOPE");

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("riskFlags")
        .hasMessageContaining("WIDE_TOKEN_ENVELOPE");
  }

  @Test
  void negativeNumericValueFailsClosedWithKeyAndValue() {
    Map<String, ContextValue> declared = new LinkedHashMap<>(declaredValues("NONE"));
    declared.put("minAgentTurns", string("-1"));

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minAgentTurns")
        .hasMessageContaining("-1");
  }

  @Test
  void fractionalNumericValueFailsClosedWithKeyAndValue() {
    Map<String, ContextValue> declared = new LinkedHashMap<>(declaredValues("NONE"));
    declared.put("estimatedInputTokensPerAgentTurn",
        new NumberContextValue(900.5, com.agentforge4j.core.workflow.context.ContextProvenance.LLM_GENERATED));

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("estimatedInputTokensPerAgentTurn")
        .hasMessageContaining("900.5");
  }

  @Test
  void outOfIntRangeNumericValueFailsClosedWithKeyAndValue() {
    Map<String, ContextValue> declared = new LinkedHashMap<>(declaredValues("NONE"));
    declared.put("estimatedInputTokensPerAgentTurn",
        new NumberContextValue(
            Long.MAX_VALUE, com.agentforge4j.core.workflow.context.ContextProvenance.LLM_GENERATED));

    assertThatThrownBy(() -> AGGREGATOR.aggregate(() -> declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("estimatedInputTokensPerAgentTurn")
        .hasMessageContaining(String.valueOf(Long.MAX_VALUE));
  }

  private static String stringValue(Map<String, ContextValue> result, String key) {
    return ((StringContextValue) result.get(key)).value();
  }

  private static long numberValue(Map<String, ContextValue> result, String key) {
    return ((NumberContextValue) result.get(key)).value().longValue();
  }

  private static List<String> riskFlagNames(Map<String, ContextValue> result) {
    return ((ContextValueList) result.get("riskFlags")).values().stream()
        .map(v -> ((StringContextValue) v).value())
        .toList();
  }
}
