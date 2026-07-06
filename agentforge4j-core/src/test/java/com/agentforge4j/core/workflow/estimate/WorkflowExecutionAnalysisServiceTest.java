// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowExecutionAnalysisServiceTest {

  private static WorkflowDefinition singleAgentWorkflow() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1").withName("S1")
        .withBehaviour(new AgentBehaviour("agent-a", StepTransition.AUTO, null))
        .build();
    return new WorkflowDefinition("wf", "W", null, null, null, "1.0.0", null, null, null,
        Map.of(), Map.of(), List.<Executable>of(step));
  }

  @Test
  void analyzeRejectsNull() {
    assertThatThrownBy(() -> WorkflowExecutionAnalysisService.analyze(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void analyzeProducesAnalysisForTheWorkflow() {
    WorkflowComplexityAnalysis analysis =
        WorkflowExecutionAnalysisService.analyze(singleAgentWorkflow());

    assertThat(analysis.workflowId()).isEqualTo("wf");
    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.SIMPLE);
  }

  @Test
  void summarizeSerialisesTheAnalysisToJson() {
    WorkflowComplexityAnalysis analysis =
        WorkflowExecutionAnalysisService.analyze(singleAgentWorkflow());

    String summary = WorkflowExecutionAnalysisService.summarize(analysis);

    assertThat(summary)
        .contains("\"workflowId\":\"wf\"")
        .contains("\"complexityClass\":\"SIMPLE\"")
        .contains("\"minimumRequiredTokens\"");
  }

  @Test
  void aggregateProducesTheFinalEstimate() {
    WorkflowComplexityAnalysis analysis =
        WorkflowExecutionAnalysisService.analyze(singleAgentWorkflow());

    ExecutionEstimate estimate =
        WorkflowExecutionAnalysisService.aggregate(analysis, new SizingInputs(100, 50, 1));

    assertThat(estimate.workflowId()).isEqualTo("wf");
    assertThat(estimate.estimatedMinTokens()).isLessThanOrEqualTo(estimate.estimatedMaxTokens());
    assertThat(estimate.recommendation()).isEqualTo(Recommendation.CONTINUE);
  }
}
