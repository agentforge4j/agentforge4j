// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AggregateBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.AssignContextBehaviour;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end guard that the built-in {@code workflow-execution-estimate} {@link
 * com.agentforge4j.core.spi.aggregation.ContextAggregator} is wired into a default-assembled
 * runtime through {@link java.util.ServiceLoader} discovery — with no embedder-supplied
 * aggregators. An {@code AGGREGATE} step selecting {@code workflow-execution-estimate} over
 * deterministically assigned context completes with the full disclosure envelope; were the
 * aggregator not discovered the same step would fail closed with {@code no ContextAggregator
 * registered for aggregatorId 'workflow-execution-estimate'}.
 */
class BuiltInContextAggregatorDiscoveryTest {

  private static final List<String> DECLARED_INPUT_KEYS = List.of(
      "complexity", "stepCount", "minimumRequiredTokens", "minAgentTurns", "expectedAgentTurns",
      "maxAgentTurns", "iterationCeiling", "riskFlags", "estimatedInputTokensPerAgentTurn",
      "estimatedOutputTokensPerAgentTurn", "estimatedToolInvocationsPerAgentTurn");

  @Test
  void builtInWorkflowExecutionEstimateAggregatorIsDiscoveredAndResolvesWithoutEmbedderRegistration() {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", workflow())))
        .withWorkflowStateRepository(stateRepository)
        .withFileSink(FileSink.NO_OP_FILE_SINK)
        .build();

    String runId = af.runtime().start("wf1");

    WorkflowState state = stateRepository.findById(runId).orElseThrow();
    assertThat(state.getStatus())
        .as("the discovered built-in workflow-execution-estimate aggregator must resolve and complete")
        .isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getContextValue("estimate.recommendation")).isPresent();
    assertThat(state.getContextValue("estimate.complexity")).isPresent();
  }

  private static WorkflowDefinition workflow() {
    List<Executable> steps = new ArrayList<>(assignSteps());
    steps.add(StepDefinition.builder()
        .withStepId("aggregate")
        .withName("Aggregate")
        .withBehaviour(new AggregateBehaviour(
            "workflow-execution-estimate", "estimate", StepTransition.AUTO))
        .withContextMapping(new ContextMapping(DECLARED_INPUT_KEYS, List.of()))
        .build());
    return new WorkflowDefinition(
        "wf1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), steps, List.of());
  }

  private static List<StepDefinition> assignSteps() {
    return List.of(
        assign("assign-complexity", "complexity",
            new StringContextValue("SIMPLE", ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-stepCount", "stepCount", new NumberContextValue(1, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-minimumRequiredTokens", "minimumRequiredTokens",
            new NumberContextValue(100, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-minAgentTurns", "minAgentTurns", new NumberContextValue(1, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-expectedAgentTurns", "expectedAgentTurns",
            new NumberContextValue(1, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-maxAgentTurns", "maxAgentTurns", new NumberContextValue(1, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-iterationCeiling", "iterationCeiling",
            new NumberContextValue(1, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-riskFlags", "riskFlags",
            new StringContextValue("NONE", ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-estimatedInputTokensPerAgentTurn", "estimatedInputTokensPerAgentTurn",
            new NumberContextValue(100, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-estimatedOutputTokensPerAgentTurn", "estimatedOutputTokensPerAgentTurn",
            new NumberContextValue(50, ContextProvenance.SYSTEM_GENERATED)),
        assign("assign-estimatedToolInvocationsPerAgentTurn", "estimatedToolInvocationsPerAgentTurn",
            new NumberContextValue(0, ContextProvenance.SYSTEM_GENERATED)));
  }

  private static StepDefinition assign(String stepId, String contextKey,
      com.agentforge4j.core.workflow.context.ContextValue value) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new AssignContextBehaviour(contextKey, value))
        .withContextMapping(ContextMapping.none())
        .build();
  }
}
