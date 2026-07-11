// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.executionestimator;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the example runs deterministically end to end: the target workflow loads as data, the
 * shipped estimator bundle runs to its approval pause with the aggregated disclosure already
 * present in context (the bundle's own {@code aggregate-estimate} step computes it before the
 * pause, not a host-side aggregation call), and the disclosure survives the approval decision.
 */
class WorkflowExecutionEstimatorExampleTest {

  private static final String ESTIMATE_PREFIX = "executionEstimate.";

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

    WorkflowState state = WorkflowExecutionEstimatorExample.estimate(agentForge4j, target);

    // Full determinism: the target has no branches/loops/prompts and the estimator's sizing is
    // scripted, so every figure is pinned exactly rather than merely bounded. The run completes
    // (the only step after the estimator's approval gate) with the disclosure fields still present.
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(stringValueOf(state, "complexity")).isEqualTo("MODERATE");
    assertThat(stringValueOf(state, "confidence")).isEqualTo("HIGH");
    assertThat(numberValueOf(state, "minimumRequiredTokens")).isEqualTo(1300);
    assertThat(riskFlagNamesOf(state)).isEmpty();
    assertThat(numberValueOf(state, "estimatedMinTokens")).isEqualTo(7_600);
    assertThat(numberValueOf(state, "estimatedExpectedTokens")).isEqualTo(7_600);
    assertThat(numberValueOf(state, "estimatedMaxTokens")).isEqualTo(7_600);
    assertThat(stringValueOf(state, "recommendation")).isEqualTo("CONTINUE");
  }

  private static String stringValueOf(WorkflowState state, String field) {
    ContextValue value = contextValueOf(state, field);
    assertThat(value).isInstanceOf(StringContextValue.class);
    return ((StringContextValue) value).value();
  }

  private static long numberValueOf(WorkflowState state, String field) {
    ContextValue value = contextValueOf(state, field);
    assertThat(value).isInstanceOf(NumberContextValue.class);
    return ((NumberContextValue) value).value().longValue();
  }

  private static List<String> riskFlagNamesOf(WorkflowState state) {
    ContextValue value = contextValueOf(state, "riskFlags");
    assertThat(value).isInstanceOf(ContextValueList.class);
    return ((ContextValueList) value).values().stream()
        .map(entry -> ((StringContextValue) entry).value())
        .toList();
  }

  private static ContextValue contextValueOf(WorkflowState state, String field) {
    return state.getContextValue(ESTIMATE_PREFIX + field)
        .orElseThrow(() -> new AssertionError(
            "Expected context key '%s%s' to be present".formatted(ESTIMATE_PREFIX, field)));
  }
}
