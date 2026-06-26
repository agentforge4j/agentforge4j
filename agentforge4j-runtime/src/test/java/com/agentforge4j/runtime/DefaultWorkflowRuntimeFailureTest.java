// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.requirement.DefaultRequirementResolver;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultWorkflowRuntimeFailureTest {

  @Test
  void drive_exception_after_cancelled_leaves_status_cancelled() {
  InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(inv -> {
      ExecutionContext ctx = inv.getArgument(1);
      ctx.getState().setStatus(WorkflowStatus.CANCELLED);
      throw new RuntimeException("handler failure");
    });

    DefaultWorkflowRuntime runtime = runtime(eventRecorder, stepSequenceExecutor);
    String runId = runtime.start("wf-fail");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(runtime.getState(runId).getRunFailure()).isNull();
    assertThat(eventLog.getEvents(runId).stream()
        .anyMatch(e -> e.eventType() == WorkflowEventType.RUN_FAILED))
        .isFalse();
  }

  @Test
  void drive_exception_without_prior_cancelled_sets_failed() {
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenThrow(new RuntimeException("handler failure"));

    DefaultWorkflowRuntime runtime = runtime(eventRecorder, stepSequenceExecutor);
    String runId = runtime.start("wf-fail");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(runtime.getState(runId).getRunFailure()).isNotNull();
    assertThat(eventLog.getEvents(runId).stream()
        .anyMatch(e -> e.eventType() == WorkflowEventType.RUN_FAILED))
        .isTrue();
  }

  private static DefaultWorkflowRuntime runtime(EventRecorder eventRecorder,
      StepSequenceExecutor stepSequenceExecutor) {
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-fail",
        "wf-fail",
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

    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        new InMemoryWorkflowStateRepository(),
        stepSequenceExecutor,
        eventRecorder,
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC),
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        null,
        null,
        new DefaultRequirementResolver(),
        new TransitionGate(eventRecorder),
        com.agentforge4j.runtime.interceptor.RunExecutionInterceptor.NO_OP,
        new InMemoryGeneratedArtifactStore());
  }

}
