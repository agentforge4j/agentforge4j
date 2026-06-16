// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.LlmCommandParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_MODEL;
import static com.agentforge4j.runtime.llm.AgentInvocationResultTestFixtures.TEST_TOKEN_USAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLoopEvaluatorTest {

  private AgentInvoker agentInvoker;
  private DefaultLoopEvaluator evaluator;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() {
    agentInvoker = mock(AgentInvoker.class);
    evaluator = new DefaultLoopEvaluator(agentInvoker);
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1",
        "wf-1",
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("s1")
            .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
            .withContextMapping(ContextMapping.none())
            .build()));
    WorkflowState state = new WorkflowState("run-1", workflow.id(), null,
        Instant.parse("2026-05-01T00:00:00Z"));
    executionContext = new ExecutionContext(state, workflow, 32);
    executionContext.enterWorkflow(workflow);
  }

  @Test
  void terminates_when_agent_returns_termination_signal() {
    when(agentInvoker.invoke(eq("eval-agent"), any(), any(), isNull(), isNull(), any()))
        .thenReturn(AgentInvocationResult.builder()
            .withRawResponse("raw")
            .withCommands(List.of(new CompleteCommand(null)))
            .withModelUsed(TEST_MODEL)
            .withTokenUsage(TEST_TOKEN_USAGE)
            .build());

    assertThat(evaluator.shouldTerminate("eval-agent", 2, executionContext)).isTrue();
  }

  @Test
  void continues_when_agent_returns_continue_signal() {
    when(agentInvoker.invoke(eq("eval-agent"), any(), any(), isNull(), isNull(), any()))
        .thenReturn(AgentInvocationResult.builder()
            .withRawResponse("raw")
            .withCommands(List.of(new ContinueCommand(null, null, List.of())))
            .withModelUsed(TEST_MODEL)
            .withTokenUsage(TEST_TOKEN_USAGE)
            .build());

    assertThat(evaluator.shouldTerminate("eval-agent", 2, executionContext)).isFalse();
  }

  @Test
  void malformed_agent_response_propagates_from_invoker() {
    when(agentInvoker.invoke(eq("eval-agent"), any(), any(), isNull(), isNull(), any()))
        .thenThrow(new LlmCommandParseException("bad json"));

    assertThatThrownBy(() -> evaluator.shouldTerminate("eval-agent", 1, executionContext))
        .isInstanceOf(LlmCommandParseException.class);
  }

  @Test
  void passes_state_to_evaluator_agent_invocation() {
    when(agentInvoker.invoke(eq("eval-agent"), eq(ContextMapping.none()),
        eq(executionContext.getState()), isNull(), isNull(), any()))
        .thenReturn(AgentInvocationResult.builder()
            .withRawResponse("raw")
            .withCommands(List.of())
            .withModelUsed(TEST_MODEL)
            .withTokenUsage(TEST_TOKEN_USAGE)
            .build());

    evaluator.shouldTerminate("eval-agent", 4, executionContext);

    ArgumentCaptor<WorkflowState> stateCaptor = ArgumentCaptor.forClass(WorkflowState.class);
    verify(agentInvoker).invoke(eq("eval-agent"), eq(ContextMapping.none()),
        stateCaptor.capture(), isNull(), isNull(), any());
    assertThat(stateCaptor.getValue().getRunId()).isEqualTo("run-1");
  }
}
