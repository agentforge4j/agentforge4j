// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.executionestimator;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.estimate.ComplexityClass;
import com.agentforge4j.core.workflow.estimate.ExecutionEstimate;
import com.agentforge4j.core.workflow.estimate.Recommendation;
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

    assertThat(estimate.workflowId()).isEqualTo("baby-agent-birth");
    assertThat(estimate.complexity()).isIn(ComplexityClass.SIMPLE, ComplexityClass.MODERATE);
    assertThat(estimate.estimatedMinTokens()).isLessThanOrEqualTo(estimate.estimatedExpectedTokens());
    assertThat(estimate.estimatedExpectedTokens()).isLessThanOrEqualTo(estimate.estimatedMaxTokens());
    assertThat(estimate.minimumRequiredTokens()).isPositive();
    assertThat(estimate.recommendation()).isEqualTo(Recommendation.CONTINUE);
  }
}
