// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for the {@code finaliseDrive} guard: a handler that already transitions the
 * run to {@code FAILED} and records its own {@code RUN_FAILED} (here, {@code MaxIterationsHandler}
 * on an {@code AGENT_SIGNAL} loop configured with {@code MaxIterationsAction.FAIL}) must not be
 * double-fired by the generic terminal-failure fallback in {@code DefaultWorkflowRuntime}.
 */
class RunFailedSingleEmissionRuntimeTest {

  private static final String BLUEPRINT_ID = "bp-loop";

  @Test
  void agentSignalLoopReachingMaxIterationsWithFailActionEmitsExactlyOneRunFailed() {
    StepDefinition loopBody = StepDefinition.builder()
        .withStepId("in-loop")
        .withName("in-loop")
        .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
        .build();
    LoopConfig loopConfig = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2, MaxIterationsAction.FAIL);
    BlueprintDefinition blueprint = new BlueprintDefinition(BLUEPRINT_ID, BLUEPRINT_ID,
        new BlueprintBehaviour(loopConfig, StepTransition.AUTO), List.of(loopBody));
    WorkflowDefinition workflow = workflow("wf-loop-fail",
        Map.of(BLUEPRINT_ID, blueprint), List.of(new BlueprintRef(BLUEPRINT_ID)));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(countEvents(fixture, runId, WorkflowEventType.RUN_FAILED)).isEqualTo(1);
  }

  private static long countEvents(Fixture fixture, String runId, WorkflowEventType type) {
    List<WorkflowEvent> events = fixture.eventLog().getEvents(runId);
    return events.stream().filter(event -> event.eventType() == type).count();
  }

  private static WorkflowDefinition workflow(String id,
      Map<String, BlueprintDefinition> blueprints, List<Executable> steps) {
    return new WorkflowDefinition(id, id, null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), blueprints, steps);
  }

  private static Fixture fixture(WorkflowDefinition workflow) {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(unusedAgentInvoker())
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, stateRepository, eventLog);
  }

  private static AgentInvoker unusedAgentInvoker() {
    ObjectMapper mapper = new ObjectMapper();
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    return AgentInvoker.builder()
        .agentRepository(mock(com.agentforge4j.core.agent.AgentRepository.class))
        .llmClientResolver(mock(LlmClientResolver.class))
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowStateRepository stateRepository,
                         WorkflowEventLog eventLog) {
  }
}
