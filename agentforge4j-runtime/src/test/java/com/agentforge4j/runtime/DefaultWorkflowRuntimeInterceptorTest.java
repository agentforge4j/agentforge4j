package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.requirement.DefaultRequirementResolver;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.execution.ExecutableExecutor;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.interceptor.ExecutionBlockedException;
import com.agentforge4j.runtime.interceptor.RunExecutionContext;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultWorkflowRuntimeInterceptorTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

  private final InMemoryWorkflowStateRepository stateRepo = new InMemoryWorkflowStateRepository();
  private final StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
  private final List<RunExecutionContext> mainEntries = new ArrayList<>();
  private InMemoryWorkflowEventLog eventLog;

  @Test
  void beforeMainExecutionFiresOnceOnFirstEntry() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);

    String runId = runtime(recording()).start("wf-1");

    assertThat(mainEntries).hasSize(1);
    assertThat(mainEntries.get(0).runId()).isEqualTo(runId);
  }

  @Test
  void beforeMainExecutionNotFiredOnResume() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    WorkflowState resumed = new WorkflowState("run-resume", "wf-1", null, CLOCK.instant());
    resumed.setStatus(WorkflowStatus.PAUSED);
    resumed.putStepExecutionUid("s1", 1);   // a step has already executed → this is a resume
    stateRepo.save(resumed);

    runtime(recording()).continueRun("run-resume", "user");

    assertThat(mainEntries).isEmpty();
  }

  @Test
  void blockingBeforeMainExecutionPropagatesAndSkipsExecution() {
    RunExecutionInterceptor blocker = new RunExecutionInterceptor() {
      @Override
      public void beforeMainExecution(RunExecutionContext context) {
        throw new ExecutionBlockedException("insufficient credits");
      }
    };

    DefaultWorkflowRuntime runtime = runtime(blocker);
    assertThatThrownBy(() -> runtime.start("wf-1"))
        .isInstanceOf(ExecutionBlockedException.class)
        .hasMessageContaining("insufficient credits");
    verify(stepSequenceExecutor, never()).executeAll(anyList(), any());

    WorkflowState persisted = stateRepo.findAll().get(0);
    assertThat(blockedEventCount(persisted.getRunId())).isEqualTo(1L);   // neutral audit recorded
    assertThat(persisted.getStatus())                                    // run left non-terminal
        .isNotIn(WorkflowStatus.COMPLETED, WorkflowStatus.FAILED, WorkflowStatus.CANCELLED);
  }

  @Test
  void executionBlockedDuringMainExecutionPropagatesAndDoesNotFailRun() {
    when(stepSequenceExecutor.executeAll(anyList(), any()))
        .thenThrow(new ExecutionBlockedException("budget exhausted mid-run"));
    WorkflowState resumed = new WorkflowState("run-blk", "wf-1", null, CLOCK.instant());
    resumed.setStatus(WorkflowStatus.PAUSED);
    resumed.putStepExecutionUid("s1", 1);   // resume: before-main skipped, executeAll still runs
    stateRepo.save(resumed);

    assertThatThrownBy(() -> runtime(RunExecutionInterceptor.NO_OP).continueRun("run-blk", "user"))
        .isInstanceOf(ExecutionBlockedException.class)
        .hasMessageContaining("budget exhausted");

    WorkflowState after = stateRepo.findById("run-blk").orElseThrow();
    assertThat(after.getStatus()).isNotEqualTo(WorkflowStatus.FAILED);
    assertThat(after.getRunFailure()).isNull();
    assertThat(blockedEventCount("run-blk")).isEqualTo(1L);   // neutral block audit recorded
  }

  private long blockedEventCount(String runId) {
    return eventLog.getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.RUN_BLOCKED)
        .count();
  }

  private RunExecutionInterceptor recording() {
    return new RunExecutionInterceptor() {
      @Override
      public void beforeMainExecution(RunExecutionContext context) {
        mainEntries.add(context);
      }
    };
  }

  private DefaultWorkflowRuntime runtime(RunExecutionInterceptor interceptor) {
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("s1")
            .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
            .withContextMapping(ContextMapping.none())
            .build()));
    eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);
    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        stateRepo,
        stepSequenceExecutor,
        mock(ExecutableExecutor.class),
        eventRecorder,
        CLOCK,
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        null,
        null,
        new DefaultRequirementResolver(),
        new TransitionGate(eventRecorder),
        interceptor);
  }
}
