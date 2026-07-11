// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.executionestimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.estimate.ComplexityClass;
import com.agentforge4j.core.workflow.estimate.ConfidenceGrade;
import com.agentforge4j.core.workflow.estimate.ExecutionEstimate;
import com.agentforge4j.core.workflow.estimate.Recommendation;
import com.agentforge4j.core.workflow.state.WorkflowState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the example runs deterministically end to end: the target workflow loads as data, the
 * shipped estimator bundle runs to its approval pause, and the aggregated disclosure is well-formed
 * — asserted after the fact via the public return value, since the disclosure itself is printed at
 * the point the mandate requires (before the approval decision), not re-derivable afterwards.
 */
class WorkflowExecutionEstimatorExampleTest {

  @Test
  void loadsTheTargetWorkflowAsData() {
    WorkflowDefinition target = WorkflowExecutionEstimatorExample.loadTargetWorkflow();

    assertThat(target.id()).isEqualTo("baby-agent-birth");
    assertThat(target.steps()).hasSize(7);
  }

  @Test
  void estimatesTheTargetWorkflowAndProducesAWellFormedDisclosure() {
    WorkflowDefinition target = WorkflowExecutionEstimatorExample.loadTargetWorkflow();
    AgentForge4j agentForge4j = WorkflowExecutionEstimatorExample.assemble();

    ExecutionEstimate estimate = WorkflowExecutionEstimatorExample.estimate(agentForge4j, target);

    // Full determinism: the target has no branches/loops/prompts and the estimator's sizing is
    // scripted, so every figure is pinned exactly rather than merely bounded.
    assertThat(estimate.workflowId()).isEqualTo("baby-agent-birth");
    assertThat(estimate.complexity()).isEqualTo(ComplexityClass.MODERATE);
    assertThat(estimate.confidence()).isEqualTo(ConfidenceGrade.HIGH);
    assertThat(estimate.minimumRequiredTokens()).isEqualTo(1300);
    assertThat(estimate.estimatedAgentTurns()).isEqualTo(6);
    assertThat(estimate.estimatedToolInvocations()).isZero();
    assertThat(estimate.estimatedSteps()).isEqualTo(7);
    assertThat(estimate.riskFlags()).isEqualTo(List.of());
    assertThat(estimate.estimatedMinTokens()).isEqualTo(7_600);
    assertThat(estimate.estimatedExpectedTokens()).isEqualTo(7_600);
    assertThat(estimate.estimatedMaxTokens()).isEqualTo(7_600);
    assertThat(estimate.recommendation()).isEqualTo(Recommendation.CONTINUE);
  }

  private static WorkflowState stateWith(String key, NumberContextValue value) {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    state.putContextValue(key, value);
    return state;
  }

  @Test
  void readNumberRejectsAFractionalValue() {
    WorkflowState state = stateWith("estimatedInputTokensPerAgentTurn",
        new NumberContextValue(700.5, ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> WorkflowExecutionEstimatorExample.readNumber(state,
        "estimatedInputTokensPerAgentTurn"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("estimatedInputTokensPerAgentTurn")
        .hasMessageContaining("700.5");
  }

  @Test
  void readNumberRejectsANegativeValue() {
    WorkflowState state = stateWith("estimatedOutputTokensPerAgentTurn",
        new NumberContextValue(-5, ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> WorkflowExecutionEstimatorExample.readNumber(state,
        "estimatedOutputTokensPerAgentTurn"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("estimatedOutputTokensPerAgentTurn")
        .hasMessageContaining("-5");
  }

  @Test
  void readNumberRejectsAnOutOfIntRangeValue() {
    WorkflowState state = stateWith("estimatedToolInvocationsPerAgentTurn",
        new NumberContextValue(Long.MAX_VALUE, ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> WorkflowExecutionEstimatorExample.readNumber(state,
        "estimatedToolInvocationsPerAgentTurn"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("estimatedToolInvocationsPerAgentTurn")
        .hasMessageContaining("fit in an int");
  }

  @Test
  void readNumberRejectsANonNumericValue() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, Instant.EPOCH);
    state.putContextValue("estimatedInputTokensPerAgentTurn",
        new StringContextValue("seven", ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> WorkflowExecutionEstimatorExample.readNumber(state,
        "estimatedInputTokensPerAgentTurn"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("numeric");
  }
}
