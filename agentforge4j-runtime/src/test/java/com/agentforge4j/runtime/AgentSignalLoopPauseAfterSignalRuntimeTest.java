// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage: an {@code AGENT_SIGNAL} loop whose signalling iteration pauses
 * <em>after</em> the agent emits {@code COMPLETE} (here, body {@code [AGENT, INPUT]} where the INPUT
 * step pauses in the same iteration) must still terminate cleanly once the human resumes — not lose
 * the signal across the pause and spin further iterations. Driven through the real runtime
 * (start / pause / submitInput), not a unit-mocked loop iteration.
 */
class AgentSignalLoopPauseAfterSignalRuntimeTest {

  private static final String BLUEPRINT_ID = "bp-agent-signal";

  @Test
  void loopTerminatesAfterResumeWhenAgentSignalledCompleteBeforeALaterPause() {
    StepDefinition agentStep = StepDefinition.builder()
        .withStepId("agent")
        .withName("agent")
        .withBehaviour(new AgentBehaviour("agent-x", StepTransition.AUTO, RetryPolicy.none()))
        .withContextMapping(ContextMapping.none())
        .build();
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition inputStep = StepDefinition.builder()
        .withStepId("input")
        .withName("input")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 5, MaxIterationsAction.FAIL);
    BlueprintDefinition blueprint = new BlueprintDefinition(BLUEPRINT_ID, BLUEPRINT_ID,
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO), List.of(agentStep, inputStep));
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-agent-signal-pause", "wf-agent-signal-pause", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form1", form),
        Map.of(BLUEPRINT_ID, blueprint), List.of(new BlueprintRef(BLUEPRINT_ID)), List.of());

    Fixture fixture = fixture(workflow, completingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // Drive 1: the agent step signals COMPLETE, then the input step pauses in the same iteration —
    // the completion signal must survive this pause.
    WorkflowState paused = fixture.runtime().getState(runId);
    assertThat(paused.getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "agent", WorkflowEventType.STEP_STARTED)).isEqualTo(1);
    assertThat(countEvents(fixture, runId, "input", WorkflowEventType.AWAITING_INPUT)).isEqualTo(1);

    fixture.runtime().submitInput(runId, Map.of("field1", "the answer"), "user");

    // The resume must observe the persisted signal and terminate cleanly: no re-invocation of the
    // agent, no re-prompting of the input step, no spin to maxIterations/FAILED.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(fixture, runId, "agent", WorkflowEventType.STEP_STARTED)).isEqualTo(1);
    assertThat(countEvents(fixture, runId, "input", WorkflowEventType.AWAITING_INPUT)).isEqualTo(1);
  }

  private static long countEvents(Fixture fixture, String runId, String stepId,
      WorkflowEventType type) {
    List<WorkflowEvent> events = fixture.eventLog().getEvents(runId);
    return events.stream()
        .filter(event -> event.eventType() == type)
        .filter(event -> stepId.equals(event.stepId()))
        .count();
  }

  private static AgentInvoker completingAgentInvoker() {
    AgentInvoker invoker = mock(AgentInvoker.class);
    AgentInvocationResult result = AgentInvocationResult.builder()
        .withRawResponse("agent-output")
        .withCommands(List.of(new CompleteCommand(null)))
        .build();
    when(invoker.invoke(any(), any(), any(), any(), any(), any())).thenReturn(result);
    return invoker;
  }

  private static Fixture fixture(WorkflowDefinition workflow, AgentInvoker agentInvoker) {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, stateRepository, eventLog);
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowStateRepository stateRepository,
                         WorkflowEventLog eventLog) {

  }
}
