// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowExecutionAggregatorTest {

  private static WorkflowComplexityAnalysis analysis(ComplexityClass complexity,
      long minTurns, long expectedTurns, long maxTurns, long minimumRequiredTokens,
      List<RiskFlag> riskFlags) {
    return new WorkflowComplexityAnalysis("wf", 1, 1, 0, 0, 0, 0, 0,
        minTurns, expectedTurns, maxTurns, Math.max(1, maxTurns), true, null,
        minimumRequiredTokens, complexity, riskFlags);
  }

  @Test
  void buildsOrderedEnvelopeForSimpleWorkflow() {
    WorkflowComplexityAnalysis analysis =
        analysis(ComplexityClass.SIMPLE, 1, 1, 1, 300, List.of());
    SizingInputs sizing = new SizingInputs(100, 50, 1);

    ExecutionEstimate estimate = WorkflowExecutionAggregator.aggregate(analysis, sizing);

    assertThat(estimate.estimatedMinTokens()).isEqualTo(450);
    assertThat(estimate.estimatedExpectedTokens()).isEqualTo(450);
    assertThat(estimate.estimatedMaxTokens()).isEqualTo(450);
    assertThat(estimate.minimumRequiredTokens()).isEqualTo(300);
    assertThat(estimate.estimatedAgentTurns()).isEqualTo(1);
    assertThat(estimate.estimatedToolInvocations()).isEqualTo(1);
    assertThat(estimate.confidence()).isEqualTo(ConfidenceGrade.HIGH);
    assertThat(estimate.recommendation()).isEqualTo(Recommendation.CONTINUE);
    assertThat(estimate.riskFlags()).doesNotContain(RiskFlag.WIDE_TOKEN_ENVELOPE);
  }

  @Test
  void wideHighRiskEnvelopeLowersConfidenceAndRecommendsNarrow() {
    WorkflowComplexityAnalysis analysis = analysis(
        ComplexityClass.HIGH_RISK, 1, 10, 100, 300, List.of(RiskFlag.AGENT_DRIVEN_LOOP));
    SizingInputs sizing = new SizingInputs(100, 0, 0);

    ExecutionEstimate estimate = WorkflowExecutionAggregator.aggregate(analysis, sizing);

    assertThat(estimate.estimatedMinTokens()).isEqualTo(400);
    assertThat(estimate.estimatedExpectedTokens()).isEqualTo(1300);
    assertThat(estimate.estimatedMaxTokens()).isEqualTo(10300);
    assertThat(estimate.riskFlags())
        .contains(RiskFlag.AGENT_DRIVEN_LOOP, RiskFlag.WIDE_TOKEN_ENVELOPE);
    assertThat(estimate.confidence()).isEqualTo(ConfidenceGrade.VERY_LOW);
    assertThat(estimate.recommendation()).isEqualTo(Recommendation.NARROW);
  }

  @Test
  void rejectsNullArguments() {
    WorkflowComplexityAnalysis analysis =
        analysis(ComplexityClass.SIMPLE, 1, 1, 1, 300, List.of());
    assertThatThrownBy(() -> WorkflowExecutionAggregator.aggregate(null, new SizingInputs(1, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorkflowExecutionAggregator.aggregate(analysis, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
