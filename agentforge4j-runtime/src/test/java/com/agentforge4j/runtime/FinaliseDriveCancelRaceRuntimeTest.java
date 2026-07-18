// SPDX-License-Identifier: Apache-2.0
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
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.execution.ExecutionOutcome;
import com.agentforge4j.runtime.execution.StepSequenceExecutor;
import com.agentforge4j.runtime.execution.TransitionGate;
import com.agentforge4j.runtime.interceptor.RunExecutionInterceptor;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the atomic terminal-status transition between {@code cancel()} and
 * {@code DefaultWorkflowRuntime.finaliseDrive}'s {@code COMPLETED} arm: a concurrent {@code cancel()}
 * and the drive's own completion of the last step must not both apply, and whichever transition wins
 * must leave a single, non-contradictory terminal status and event.
 */
@Timeout(value = 30)
class FinaliseDriveCancelRaceRuntimeTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);

  /**
   * Deterministic, no timing sleeps: the mocked {@link StepSequenceExecutor} stands in for the last
   * step's behaviour handler, flipping the shared, in-flight {@link WorkflowState} to {@code CANCELLED}
   * (and recording {@code RUN_CANCELLED}) synchronously — from the same thread, before returning — then
   * reporting {@code ExecutionOutcome.COMPLETED}, simulating a same-thread-visible concurrent cancel
   * completing just before {@code finaliseDrive} runs. Retained alongside the genuinely concurrent test
   * below as a fast, always-reproducible pin of the same invariant.
   */
  @Test
  void concurrentCancelCompletingDuringTheLastStepStaysCancelledNotCompleted() {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      WorkflowState state = context.getState();
      state.setStatus(WorkflowStatus.CANCELLED);
      eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
          WorkflowEventType.RUN_CANCELLED, null, "test-actor");
      return ExecutionOutcome.COMPLETED;
    });

    WorkflowDefinition workflow = workflow("wf-cancel-race");

    DefaultWorkflowRuntime runtime = runtime(stateRepository, stepSequenceExecutor, eventRecorder);

    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_CANCELLED)).isEqualTo(1);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_COMPLETED)).isZero();
  }

  /**
   * Genuinely concurrent version, driven through the real {@code cancel()} API on a second thread —
   * not simulated from inside the mocked executor. The mocked {@link StepSequenceExecutor} signals a
   * latch the instant it is entered (so the test thread knows the drive is "in flight" on the last
   * step), then blocks on a second latch until the test thread's real {@code cancel()} call has
   * returned, before finally reporting {@code COMPLETED} — deterministically reproducing the race
   * window with no sleeps or timing guesses.
   */
  @Test
  void aConcurrentCancelCallWinsWhileTheFinalStepIsStillInFlight() throws InterruptedException {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    CountDownLatch driveEnteredExecuteAll = new CountDownLatch(1);
    CountDownLatch cancelReturned = new CountDownLatch(1);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      driveEnteredExecuteAll.countDown();
      cancelReturned.await();
      return ExecutionOutcome.COMPLETED;
    });

    WorkflowDefinition workflow = workflow("wf-cancel-wins-race");
    DefaultWorkflowRuntime runtime = runtime(stateRepository, stepSequenceExecutor, eventRecorder);

    // Seed a PAUSED state directly (start() would itself block inside executeAll on this thread) and
    // resume it via the real continueRun() API on a background thread, so the real cancel() call
    // below races a real drive() rather than a fabricated one.
    WorkflowState seeded = new WorkflowState("run-cancel-race", workflow.id(), null,
        Instant.parse("2026-07-01T12:00:00Z"));
    seeded.setStatus(WorkflowStatus.PAUSED);
    stateRepository.save(seeded);

    Thread driveThread = new Thread(() -> runtime.continueRun(seeded.getRunId(), "resumer"));
    driveThread.start();
    try {
      assertThat(driveEnteredExecuteAll.await(10, TimeUnit.SECONDS)).isTrue();

      // The real cancel() API, called concurrently while the drive thread is blocked inside the last
      // step: this must succeed (status is still RUNNING at this point).
      runtime.cancel(seeded.getRunId(), "operator");

      cancelReturned.countDown();
      driveThread.join();

      assertThat(runtime.getState(seeded.getRunId()).getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
      assertThat(countEvents(eventLog, seeded.getRunId(), WorkflowEventType.RUN_CANCELLED))
          .isEqualTo(1);
      assertThat(countEvents(eventLog, seeded.getRunId(), WorkflowEventType.RUN_COMPLETED)).isZero();
    } finally {
      cancelReturned.countDown();
      driveThread.join();
    }
  }

  /**
   * The reverse ordering: the drive completes (and its atomic transition to {@code COMPLETED} fully
   * applies) before {@code cancel()} is ever called. {@code cancel()} must then follow the existing
   * terminal-status contract — rejected, no {@code RUN_CANCELLED} recorded, {@code COMPLETED} left
   * untouched — with no contradictory event either way.
   */
  @Test
  void cancelAfterCompletionAlreadyAppliedFollowsTheExistingTerminalContract() {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);

    WorkflowDefinition workflow = workflow("wf-completed-before-cancel");
    DefaultWorkflowRuntime runtime = runtime(stateRepository, stepSequenceExecutor, eventRecorder);
    String runId = runtime.start(workflow.id());
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

    assertThatThrownBy(() -> runtime.cancel(runId, "operator"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("COMPLETED");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_CANCELLED)).isZero();
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_COMPLETED)).isEqualTo(1);
  }

  /**
   * The gap {@code finaliseDrive}'s {@code PAUSED} arm left open: nothing there ever checked for a
   * concurrent cancellation, because the fine-grained suspension status ({@code AWAITING_INPUT} here)
   * is deliberately left to whichever handler set it. Genuinely concurrent, via the real
   * {@code cancel()} API on a second thread: the mocked {@link StepSequenceExecutor} blocks (signalling
   * entry first) until {@code cancel()} has returned, then — simulating a step handler that is entirely
   * unaware of the concurrent cancellation — sets {@code AWAITING_INPUT} directly on the shared state
   * and reports {@code PAUSED}. {@code enforceCancellationWon} must restore {@code CANCELLED}
   * afterward, with no contradictory completion/failure event.
   */
  @Test
  void cancelWhileBlockedThenHandlerSetsAwaitingStatusAndReturnsPausedStaysCancelled()
      throws InterruptedException {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    CountDownLatch driveEnteredExecuteAll = new CountDownLatch(1);
    CountDownLatch cancelReturned = new CountDownLatch(1);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      driveEnteredExecuteAll.countDown();
      cancelReturned.await();
      ExecutionContext context = invocation.getArgument(1);
      context.getState().setStatus(WorkflowStatus.AWAITING_INPUT);
      return ExecutionOutcome.PAUSED;
    });

    WorkflowDefinition workflow = workflow("wf-cancel-then-pause");
    DefaultWorkflowRuntime runtime = runtime(stateRepository, stepSequenceExecutor, eventRecorder);

    WorkflowState seeded = new WorkflowState("run-cancel-then-pause", workflow.id(), null,
        Instant.parse("2026-07-01T12:00:00Z"));
    seeded.setStatus(WorkflowStatus.PAUSED);
    stateRepository.save(seeded);

    Thread driveThread = new Thread(() -> runtime.continueRun(seeded.getRunId(), "resumer"));
    driveThread.start();
    try {
      assertThat(driveEnteredExecuteAll.await(10, TimeUnit.SECONDS)).isTrue();
      runtime.cancel(seeded.getRunId(), "operator");
      cancelReturned.countDown();
      driveThread.join();

      assertThat(runtime.getState(seeded.getRunId()).getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
      assertThat(countEvents(eventLog, seeded.getRunId(), WorkflowEventType.RUN_CANCELLED))
          .isEqualTo(1);
      assertThat(countEvents(eventLog, seeded.getRunId(), WorkflowEventType.RUN_COMPLETED)).isZero();
      assertThat(countEvents(eventLog, seeded.getRunId(), WorkflowEventType.RUN_FAILED)).isZero();
    } finally {
      cancelReturned.countDown();
      driveThread.join();
    }
  }

  /**
   * Before this fix, {@code finaliseDrive}'s {@code COMPLETED} arm excluded only {@code CANCELLED},
   * so it could overwrite a {@code FAILED} status a handler had already committed (and already
   * recorded {@code RUN_FAILED} for) with a contradictory {@code COMPLETED}/{@code RUN_COMPLETED}.
   * Deterministic, single-threaded simulation (the same established idiom as this file's first test
   * above): the mocked executor plays the handler's already-applied failure directly on the shared
   * state before returning {@code COMPLETED} as the (mismatched) top-level outcome.
   */
  @Test
  void aHandlerCommittedFailureWinsOverAMismatchedCompletedOutcome() {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      WorkflowState state = context.getState();
      state.setStatus(WorkflowStatus.FAILED);
      eventRecorder.record(state.getRunId(), state.getCurrentStepId(),
          WorkflowEventType.RUN_FAILED, null, "runtime");
      return ExecutionOutcome.COMPLETED;
    });

    WorkflowDefinition workflow = workflow("wf-failed-before-completed-outcome");
    DefaultWorkflowRuntime runtime = runtime(stateRepository, stepSequenceExecutor, eventRecorder);
    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_FAILED)).isEqualTo(1);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_COMPLETED)).isZero();
  }

  /**
   * The reverse ordering: before this fix, {@code failRun} excluded only {@code CANCELLED}, so an
   * exception reaching {@code drive()}'s catch block after the run had already committed to
   * {@code COMPLETED} (and already recorded {@code RUN_COMPLETED}) would still overwrite it with a
   * contradictory {@code FAILED}/{@code RUN_FAILED}. Same deterministic, single-threaded idiom: the
   * mocked executor commits {@code COMPLETED} directly on the shared state, then throws.
   */
  @Test
  void aHandlerCommittedCompletionWinsOverASubsequentThrowLeadingToFailRun() {
    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
    InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

    when(stepSequenceExecutor.executeAll(anyList(), any())).thenAnswer(invocation -> {
      ExecutionContext context = invocation.getArgument(1);
      WorkflowState state = context.getState();
      state.setStatus(WorkflowStatus.COMPLETED);
      eventRecorder.record(state.getRunId(), null,
          WorkflowEventType.RUN_COMPLETED, null, "runtime");
      throw new IllegalStateException("unexpected failure after commit");
    });

    WorkflowDefinition workflow = workflow("wf-completed-before-throw");
    DefaultWorkflowRuntime runtime = runtime(stateRepository, stepSequenceExecutor, eventRecorder);
    String runId = runtime.start(workflow.id());

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_COMPLETED)).isEqualTo(1);
    assertThat(countEvents(eventLog, runId, WorkflowEventType.RUN_FAILED)).isZero();
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

  private static DefaultWorkflowRuntime runtime(InMemoryWorkflowStateRepository stateRepository,
      StepSequenceExecutor stepSequenceExecutor, EventRecorder eventRecorder) {
    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of(
            "wf-cancel-race", workflow("wf-cancel-race"),
            "wf-cancel-wins-race", workflow("wf-cancel-wins-race"),
            "wf-completed-before-cancel", workflow("wf-completed-before-cancel"),
            "wf-cancel-then-pause", workflow("wf-cancel-then-pause"),
            "wf-failed-before-completed-outcome", workflow("wf-failed-before-completed-outcome"),
            "wf-completed-before-throw", workflow("wf-completed-before-throw"))),
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
  }

  private static long countEvents(InMemoryWorkflowEventLog eventLog, String runId,
      WorkflowEventType type) {
    return eventLog.getEvents(runId).stream()
        .filter(event -> event.eventType() == type)
        .count();
  }
}
