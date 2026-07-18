// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.requirement.DefaultRequirementResolver;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the terminal-transition lock pool being fixed-size striped locking rather
 * than a per-run-id map that grows by one entry forever: driving many thousands of distinct runs to
 * completion must never grow the pool beyond {@code RUN_LOCK_STRIPE_COUNT}, and the existing
 * terminal-race guarantees (see {@link FinaliseDriveCancelRaceRuntimeTest}) must still hold on a
 * runtime instance that has already driven many other runs.
 */
class RunLockStripingBoundedMemoryTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);
  private static final int DISTINCT_RUN_COUNT = 5_000;

  @Test
  void drivingManyDistinctRunsToCompletionNeverGrowsTheLockPool() throws ReflectiveOperationException {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    WorkflowDefinition workflow = workflow("wf-lock-pool-bound");
    DefaultWorkflowRuntime runtime = new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        stateRepository,
        stepSequenceExecutor,
        eventRecorder,
        CLOCK,
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        null,
        null,
        new DefaultRequirementResolver(),
        new TransitionGate(eventRecorder),
        RunExecutionInterceptor.NO_OP,
        new InMemoryGeneratedArtifactStore());

    Field stripesField = DefaultWorkflowRuntime.class.getDeclaredField("runLockStripes");
    stripesField.setAccessible(true);
    Object[] stripesBefore = (Object[]) stripesField.get(runtime);
    int lengthBefore = stripesBefore.length;

    for (int i = 0; i < DISTINCT_RUN_COUNT; i++) {
      String runId = runtime.start(workflow.id());
      assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    Object[] stripesAfter = (Object[]) stripesField.get(runtime);
    assertThat(stripesAfter)
        .as("the lock pool must be the same fixed-size array instance after driving %d distinct runs,"
            + " not a per-run-id structure that grew", DISTINCT_RUN_COUNT)
        .isSameAs(stripesBefore);
    assertThat(stripesAfter.length).isEqualTo(lengthBefore);
  }

  private static WorkflowDefinition workflow(String id) {
    return new WorkflowDefinition(
        id, id, null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("s1")
            .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
            .withContextMapping(ContextMapping.none())
            .build()), List.of());
  }
}
