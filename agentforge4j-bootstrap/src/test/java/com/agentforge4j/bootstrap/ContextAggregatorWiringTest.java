// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.spi.aggregation.AggregationContext;
import com.agentforge4j.core.spi.aggregation.ContextAggregator;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AggregateBehaviour;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Bootstrap wiring guard for {@link AgentForge4jBootstrap.Builder#withContextAggregators(List)}: a
 * supplied aggregator is resolvable by an {@code AGGREGATE} step that selects it by id (appended to
 * the built-in, ServiceLoader-discovered set). Without registering it, the same step fails closed
 * with the unresolved-aggregator message.
 */
class ContextAggregatorWiringTest {

  private static final String CUSTOM_ID = "custom-aggregator";

  @Test
  void supplied_aggregator_is_resolvable_by_an_aggregate_step() {
    RecordingAggregator aggregator = new RecordingAggregator();
    Fixture f = fixture(List.of(aggregator));

    String runId = f.af().runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(aggregator.invoked()).as("supplied aggregator must be resolved and run").isTrue();
  }

  @Test
  void aggregate_step_fails_closed_when_its_aggregator_is_not_registered() {
    Fixture f = fixture(List.of());

    String runId = f.af().runtime().start("wf1");

    WorkflowState state = f.stateRepository().findById(runId).orElseThrow();
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(state.getFailureReason()).contains("no ContextAggregator registered", CUSTOM_ID);
  }

  private static Fixture fixture(List<ContextAggregator> aggregators) {
    StepDefinition aggregate = StepDefinition.builder()
        .withStepId("aggregate")
        .withName("Aggregate")
        .withBehaviour(new AggregateBehaviour(CUSTOM_ID, "out", StepTransition.AUTO))
        .withContextMapping(ContextMapping.none())
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(aggregate), List.of());

    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf1", wf)))
        .withWorkflowStateRepository(stateRepository)
        .withFileSink(FileSink.NO_OP_FILE_SINK)
        .withContextAggregators(aggregators)
        .build();

    return new Fixture(af, stateRepository);
  }

  /** Records that it was invoked and returns an empty result. */
  private static final class RecordingAggregator implements ContextAggregator {

    private boolean invoked;

    @Override
    public String aggregatorId() {
      return CUSTOM_ID;
    }

    @Override
    public Map<String, ContextValue> aggregate(AggregationContext context) {
      invoked = true;
      return Map.of("noted", new StringContextValue("ok", ContextProvenance.SYSTEM_GENERATED));
    }

    boolean invoked() {
      return invoked;
    }
  }

  private record Fixture(AgentForge4j af, WorkflowStateRepository stateRepository) {

  }
}
