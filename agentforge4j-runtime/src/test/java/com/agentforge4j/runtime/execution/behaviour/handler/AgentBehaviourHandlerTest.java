// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.handler;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.SetContextCommand;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandApplier;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBehaviourHandlerTest {

  private static final String STEP_ID = "po-refine";

  private AgentInvoker agentInvoker;
  private CommandApplier commandApplier;
  private AgentBehaviourHandler handler;
  private WorkflowState state;
  private ExecutionContext executionContext;
  private StepDefinition step;

  @BeforeEach
  void setUp() {
    agentInvoker = mock(AgentInvoker.class);
    commandApplier = mock(CommandApplier.class);
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    handler = new AgentBehaviourHandler(agentInvoker, commandApplier, eventRecorder);

    step = StepDefinition.builder()
        .withStepId(STEP_ID)
        .withName("PO Refinement Iteration")
        .withBehaviour(new AgentBehaviour("app-creator-po-agent", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();

    state = new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T12:00:00Z"));
    state.setCurrentStepId(STEP_ID);
    state.putStepExecutionUid(STEP_ID, 1);
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE,
        Map.of(), Map.of(), List.of(step), List.of(), List.of());
    executionContext = new ExecutionContext(state, workflow, 32);
    executionContext.enterWorkflow(workflow);
  }

  @Test
  void complete_command_sets_agent_completion_signal_without_changing_outcome() {
    stubAgentResponse(new CompleteCommand(null));
    when(commandApplier.apply(any(), any(), any(), any(), anyInt()))
        .thenReturn(CommandApplicationResult.COMPLETE_SIGNAL);

    ExecutionOutcome outcome = handler.handle(step, (AgentBehaviour) step.behaviour(),
        executionContext);

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(executionContext.isAgentCompletionSignalled()).isTrue();
  }

  @Test
  void continue_command_clears_agent_completion_signal() {
    // Stale signal from an earlier step must be cleared when this step does not signal completion.
    executionContext.setAgentCompletionSignalled(true);
    stubAgentResponse(new SetContextCommand("key",
        new StringContextValue("value", ContextProvenance.LLM_GENERATED)));
    when(commandApplier.apply(any(), any(), any(), any(), anyInt())).thenReturn(CommandApplicationResult.CONTINUE);

    ExecutionOutcome outcome = handler.handle(step, (AgentBehaviour) step.behaviour(), executionContext);

    assertThat(outcome).isEqualTo(ExecutionOutcome.COMPLETED);
    assertThat(executionContext.isAgentCompletionSignalled()).isFalse();
  }

  private void stubAgentResponse(com.agentforge4j.core.command.LlmCommand command) {
    AgentInvocationResult result = AgentInvocationResult.builder()
        .withRawResponse("raw response")
        .withCommands(List.of(command))
        .build();
    when(agentInvoker.invoke(any(), any(), any(), any(), any(), any())).thenReturn(result);
  }
}
