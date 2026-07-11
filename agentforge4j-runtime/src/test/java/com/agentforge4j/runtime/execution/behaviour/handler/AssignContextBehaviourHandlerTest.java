// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AssignContextBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssignContextBehaviourHandlerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void assigns_value_with_system_generated_provenance_and_completes() {
    WorkflowState state = new WorkflowState("run-1", "wf-1", null, CLOCK.instant());
    state.setCurrentStepId("assign");
    AssignContextBehaviour behaviour = new AssignContextBehaviour("recommendedTier",
        new StringContextValue("POWERFUL", ContextProvenance.SYSTEM_GENERATED));
    StepDefinition step = StepDefinition.builder()
        .withStepId("assign")
        .withName("Assign")
        .withBehaviour(behaviour)
        .withContextMapping(ContextMapping.none())
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf-1", "W", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        List.of(), List.of());
    AssignContextBehaviourHandler handler =
        new AssignContextBehaviourHandler(new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK));

    ExecutionOutcome outcome = handler.handle(step, behaviour, new ExecutionContext(state, wf, 32));

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    ContextValue stored = state.getContextValue("recommendedTier").orElseThrow();
    assertThat(stored).isInstanceOf(StringContextValue.class);
    assertThat(((StringContextValue) stored).value()).isEqualTo("POWERFUL");
    assertThat(stored.provenance()).isEqualTo(ContextProvenance.SYSTEM_GENERATED);
  }
}
