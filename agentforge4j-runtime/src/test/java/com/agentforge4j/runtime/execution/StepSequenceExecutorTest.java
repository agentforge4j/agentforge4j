package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepSequenceExecutorTest {

  @Test
  void step_uids_start_at_one_for_each_execution_context() {
    StepDefinition step = StepDefinition.builder()
        .withStepId("s1")
        .withName("S")
        .withBehaviour(new AgentBehaviour("a1", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
    WorkflowDefinition wf = new WorkflowDefinition(
        "wf1",
        "W",
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(step));

    WorkflowState state1 = new WorkflowState("r1", "wf1", null, Instant.parse("2026-05-01T00:00:00Z"));
    ExecutionContext ctx1 = new ExecutionContext(state1, wf, 32);
    WorkflowState state2 = new WorkflowState("r2", "wf1", null, Instant.parse("2026-05-01T00:00:00Z"));
    ExecutionContext ctx2 = new ExecutionContext(state2, wf, 32);

    ExecutableExecutor exec = mock(ExecutableExecutor.class);
    when(exec.execute(any(Executable.class), any())).thenReturn(ExecutionOutcome.COMPLETED);

    StepSequenceExecutor executor = new StepSequenceExecutor(exec);
    executor.executeAll(List.of(step), ctx1);
    executor.executeAll(List.of(step), ctx2);

    assertThat(state1.getStepExecutionUid().get("s1")).isEqualTo(1);
    assertThat(state2.getStepExecutionUid().get("s1")).isEqualTo(1);
  }
}
