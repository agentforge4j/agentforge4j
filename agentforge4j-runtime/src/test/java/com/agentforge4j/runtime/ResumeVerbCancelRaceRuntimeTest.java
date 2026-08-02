// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.requirement.DefaultRequirementResolver;
import com.agentforge4j.core.workflow.state.WorkflowState;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L4-01 regression coverage for the resume-verb cancellation window: every resume verb's status
 * guard runs off-lock, so a concurrent {@code cancel()} can land between that guard and the verb's
 * {@code RUNNING} transition. The transition must lose — rejecting the resume — instead of
 * clobbering the durable {@code CANCELLED} status and letting the drive record a terminal lifecycle
 * event after {@code RUN_CANCELLED}. Also covers the L4-04 write-path defense: raw end-user answer
 * keys must never reach the reserved {@code __} context namespace.
 */
class ResumeVerbCancelRaceRuntimeTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);

  private final InMemoryWorkflowStateRepository stateRepo = new InMemoryWorkflowStateRepository();
  private final StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
  private final InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();

  /**
   * Deterministic reproduction of the guard-to-RUNNING window, no sleeps: {@code submitInput}'s
   * status guard has already passed when it records {@code CONTEXT_UPDATED} for the written
   * answers, and its {@code RUNNING} transition comes after — so an event-log hook firing the real
   * {@code cancel()} on that append lands the cancellation exactly inside the window. Before the
   * fix, the {@code RUNNING} write clobbered {@code CANCELLED} and the drive completed, recording
   * {@code RUN_COMPLETED} after {@code RUN_CANCELLED}; now the resume verb rejects at its
   * cancellation-aware transition and the drive never runs.
   */
  @Test
  void aCancelLandingBetweenTheResumeGuardAndTheRunningTransitionRejectsTheResume() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    AtomicReference<DefaultWorkflowRuntime> runtimeRef = new AtomicReference<>();
    AtomicBoolean fired = new AtomicBoolean(false);
    HookedEventLog hookedLog = new HookedEventLog(eventLog, event -> {
      if (event.eventType() == WorkflowEventType.CONTEXT_UPDATED && fired.compareAndSet(false, true)) {
        runtimeRef.get().cancel(event.runId(), "operator");
      }
    });
    DefaultWorkflowRuntime runtime = runtime(hookedLog);
    runtimeRef.set(runtime);
    seedAwaitingInput("run-race");

    assertThatThrownBy(() -> runtime.submitInput("run-race", Map.of("q1", "hello"), "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");

    verify(stepSequenceExecutor, never()).executeAll(anyList(), any());
    assertThat(stateRepo.findById("run-race").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.CANCELLED);
    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-race").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes).containsOnlyOnce(WorkflowEventType.RUN_CANCELLED);
    assertThat(eventTypes).doesNotContain(
        WorkflowEventType.RUN_COMPLETED, WorkflowEventType.RUN_FAILED,
        WorkflowEventType.RUN_BLOCKED);
  }

  /**
   * A cancellation that fully landed before the verb is rejected by the verb's own off-lock guard
   * already — the lock-held re-check is the racing sibling above. Positive control: without a
   * concurrent cancel the same resume completes normally.
   */
  @Test
  void withoutAConcurrentCancelTheSameResumeCompletes() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    DefaultWorkflowRuntime runtime = runtime(eventLog);
    seedAwaitingInput("run-clean");

    runtime.submitInput("run-clean", Map.of("q1", "hello"), "user");

    assertThat(stateRepo.findById("run-clean").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(eventLog.getEvents("run-clean").stream()
        .map(WorkflowEvent::eventType))
        .containsOnlyOnce(WorkflowEventType.RUN_COMPLETED);
  }

  /**
   * L4-04 write-path defense: a raw end-user answer key in the reserved {@code __} namespace is
   * rejected before any write or clear — regardless of what the step's {@code outputKeys} declare
   * (a reserved declaration is itself impossible since {@code ContextMapping} rejects it at
   * construction). Nothing is written, no {@code CONTEXT_UPDATED} is recorded, and the run stays
   * suspended awaiting a valid submission.
   */
  @Test
  void reservedNamespaceAnswerKeysAreRejectedBeforeAnyWrite() {
    DefaultWorkflowRuntime runtime = runtime(eventLog);
    seedAwaitingInput("run-guard");

    assertThatThrownBy(() -> runtime.submitInput("run-guard",
        Map.of("__retry_policy_attempts:s1", "99"), "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");

    WorkflowState after = stateRepo.findById("run-guard").orElseThrow();
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(after.getContextValue("__retry_policy_attempts:s1")).isEmpty();
    assertThat(after.getContextValue("form.__retry_policy_attempts:s1")).isEmpty();
    assertThat(eventLog.getEvents("run-guard").stream()
        .map(WorkflowEvent::eventType)
        .filter(type -> type == WorkflowEventType.CONTEXT_UPDATED)).isEmpty();
  }

  /**
   * L4-04 write-path defense, null-answer clear sibling: a reserved key must not be clearable
   * either — the rejection runs ahead of the declared-output clear path, so a seeded reserved
   * counter survives the attempted submission untouched.
   */
  @Test
  void reservedNamespaceAnswerKeysCannotClearSeededCounters() {
    DefaultWorkflowRuntime runtime = runtime(eventLog);
    WorkflowState seeded = seedAwaitingInput("run-clear");
    seeded.putContextValue("__retry_policy_attempts:s1",
        new StringContextValue("2", ContextProvenance.SYSTEM_GENERATED));

    Map<String, String> answers = new HashMap<>();
    answers.put("__retry_policy_attempts:s1", null);
    assertThatThrownBy(() -> runtime.submitInput("run-clear", answers, "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");

    WorkflowState after = stateRepo.findById("run-clear").orElseThrow();
    assertThat(after.getContextValue("__retry_policy_attempts:s1")).isPresent();
  }

  private WorkflowState seedAwaitingInput(String runId) {
    WorkflowState state = new WorkflowState(runId, "wf-1", null, CLOCK.instant());
    state.setStatus(WorkflowStatus.AWAITING_INPUT);
    state.setCurrentStepId("s1");
    state.setPendingArtifact(new ArtifactDefinition("form",
        List.of(new TextArtifactItem("q1", "Question", false, null))));
    stateRepo.save(state);
    return state;
  }

  private DefaultWorkflowRuntime runtime(WorkflowEventLog log) {
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(StepDefinition.builder()
            .withStepId("s1")
            .withName("s1")
            .withBehaviour(new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO))
            .withContextMapping(ContextMapping.none())
            .build()), List.of());
    EventRecorder eventRecorder = new EventRecorder(log, CLOCK);
    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)),
        stateRepo,
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

  /**
   * Delegates every append to the wrapped log, invoking the hook first — used to fire a real
   * {@code cancel()} at an exact, deterministic point inside a resume verb's execution.
   */
  private record HookedEventLog(WorkflowEventLog delegate,
                                Consumer<WorkflowEvent> hook) implements WorkflowEventLog {

    @Override
    public void append(WorkflowEvent event) {
      hook.accept(event);
      delegate.append(event);
    }

    @Override
    public List<WorkflowEvent> getEvents(String runId) {
      return delegate.getEvents(runId);
    }
  }
}
