// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.PolicyDenialTerminalException;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolInvocationClaimLostException;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.context.StringContextValue;
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
import com.agentforge4j.runtime.tool.DefaultToolExecutionService;
import com.agentforge4j.runtime.tool.InMemoryIntegrationRepository;
import com.agentforge4j.runtime.tool.InMemoryPendingToolInvocationStore;
import com.agentforge4j.runtime.tool.IntegrationToolProviderResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Policy-denial and tool-execution-chokepoint regressions driven through the REAL
 * {@link DefaultToolExecutionService} (no stub), asserting actual provider invocation counts.
 * Complements
 * {@link DefaultWorkflowRuntimeToolDecisionTest}, which drives {@code resolveToolDecision} /
 * {@code continueAfterToolApproval} against a scripted stub and so cannot observe whether the
 * chokepoint itself ever reaches the provider.
 */
@Timeout(value = 30)
class DefaultWorkflowRuntimeToolDecisionRealServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String CAPABILITY = "github.create_pull_request";

  private final InMemoryWorkflowStateRepository stateRepo = new InMemoryWorkflowStateRepository();
  private final InMemoryPendingToolInvocationStore store = new InMemoryPendingToolInvocationStore();
  private final StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
  private final CountingToolProvider provider = new CountingToolProvider();
  private final InMemoryWorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
  private final EventRecorder eventRecorder = new EventRecorder(eventLog, CLOCK);

  @Test
  void retryAfterPolicyDenialNeverInvokesProviderAndThrowsATypedTerminalException() {
    DefaultToolExecutionService toolService = toolService(deny("nope"));
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    ToolExecutionOutcome denyOutcome = toolService.execute(command, ctx(state));
    assertThat(denyOutcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);

    assertThatThrownBy(() -> runtime.resolveToolDecision(
        "run-1", command.toolInvocationId(), new ToolDecision.Retry("op-1")))
        .isInstanceOf(PolicyDenialTerminalException.class);

    assertThat(provider.invocations).isZero();
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);
  }

  /**
   * L4-03 regression: a pre-peek concurrent claim loss on {@code continueAfterToolApproval} — the
   * row was already claimed and fully resolved by a concurrent resolution before this call's own
   * peek — must surface as the typed, benign {@link ToolInvocationClaimLostException} its javadoc
   * promises (mirroring {@code resolveToolDecision}'s classification of the identical race), never
   * as a caller-error {@code IllegalArgumentException}. No run state is mutated.
   */
  @Test
  void prePeekClaimLossOnContinueAfterToolApprovalThrowsTheTypedClaimLostSignal() {
    DefaultToolExecutionService toolService = toolService(requireApproval());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    stateRepo.save(state);
    // Simulate the concurrent winner: the row is claimed and resolved before this call peeks.
    store.remove("run-1", command.toolInvocationId());

    DefaultWorkflowRuntime runtime = runtime(toolService);

    assertThatThrownBy(() -> runtime.continueAfterToolApproval(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice")))
        .isInstanceOf(ToolInvocationClaimLostException.class);

    assertThat(provider.invocations).isZero();
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_TOOL_APPROVAL);
  }

  @Test
  void directResumeApproveOnAPolicyDeniedRowNeverInvokesProvider() {
    DefaultToolExecutionService toolService = toolService(deny("nope"));
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));

    assertThatThrownBy(() -> toolService.resume(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice")))
        .isInstanceOf(PolicyDenialTerminalException.class);

    assertThat(provider.invocations).isZero();
    // The rejected Approve leaves the row in place so a later Reject/Continue can still resolve it.
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
  }

  @Test
  void policyDeniedRowIsStillResolvableByRejectAfterARejectedApproveAttempt() {
    DefaultToolExecutionService toolService = toolService(deny("nope"));
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));

    assertThatThrownBy(() -> toolService.resume(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice")))
        .isInstanceOf(PolicyDenialTerminalException.class);

    ToolExecutionOutcome rejected = toolService.resume(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Reject("bob", "confirmed deny"));

    assertThat(rejected.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    assertThat(provider.invocations).isZero();
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
  }

  /**
   * End-to-end proof, through the real runtime and real chokepoint (no stub), that a rejected
   * {@code Retry} against a policy denial never strands the run: the pending row survives the
   * rejected {@code Retry} intact, the run stays suspended in {@code AWAITING_TOOL_DECISION}, the
   * provider is never invoked, and the legitimate non-executing {@link ToolDecision.Continue}
   * still resolves the run afterward with no contradictory {@code TOOL_INVOCATION_COMPLETED} (or
   * any other execution-success) event ever recorded for the denied invocation.
   */
  @Test
  void rejectedRetryLeavesTheRunResolvableByContinueWithNoContradictoryCompletionEvent() {
    DefaultToolExecutionService toolService = toolService(deny("nope"));
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    ToolExecutionOutcome denyOutcome = toolService.execute(command, ctx(state));
    assertThat(denyOutcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);

    // 1-4: policy denies, Retry is attempted and rejected, provider count stays zero, pending
    // denial state stays coherent (row intact, run still suspended).
    assertThatThrownBy(() -> runtime.resolveToolDecision(
        "run-1", command.toolInvocationId(), new ToolDecision.Retry("op-1")))
        .isInstanceOf(PolicyDenialTerminalException.class);
    assertThat(provider.invocations).isZero();
    assertThat(store.find("run-1", command.toolInvocationId())).isNotNull();
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);

    // 5: the legitimate terminal action (the runtime's non-executing Continue) still resolves the
    // run — the rejected Retry did not consume or corrupt the pending row.
    WorkflowState resumed = runtime.resolveToolDecision(
        "run-1", command.toolInvocationId(), new ToolDecision.Continue("op-2"));

    assertThat(provider.invocations).isZero();
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
    assertThat(resumed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(contextValue("tool." + CAPABILITY + ".error")).isEqualTo("nope");

    // 6: no contradictory completion/execution event for the denied invocation was ever recorded
    // — only the denial/override-rejected audit trail, never a TOOL_INVOCATION_COMPLETED implying
    // the provider ran.
    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes).doesNotContain(WorkflowEventType.TOOL_INVOCATION_COMPLETED);
    assertThat(eventTypes).contains(
        WorkflowEventType.TOOL_INVOCATION_DENIED,
        WorkflowEventType.TOOL_INVOCATION_OVERRIDE_REJECTED,
        WorkflowEventType.RUN_COMPLETED);
    assertThat(eventTypes.stream().filter(t -> t == WorkflowEventType.RUN_COMPLETED).count())
        .isEqualTo(1);
  }

  @Test
  void continueAfterToolApprovalOnALegitimateApprovalRequiredRowExecutes() {
    DefaultToolExecutionService toolService = toolService(requireApproval());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);
    runtime.continueAfterToolApproval(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice"));

    assertThat(provider.invocations).isEqualTo(1);
    assertThat(contextValue("tool." + CAPABILITY)).isEqualTo(CountingToolProvider.SUCCESS_OUTPUT);
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void retryOfAnEligibleExecutionFailureStillReplaysTheCall() {
    provider.failFirstInvocation = true;
    DefaultToolExecutionService toolService = toolService(allow());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    ToolExecutionOutcome first = toolService.execute(command, ctx(state));
    assertThat(first.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);
    runtime.resolveToolDecision("run-1", command.toolInvocationId(), new ToolDecision.Retry("op-1"));

    assertThat(provider.invocations).isEqualTo(2);
    assertThat(contextValue("tool." + CAPABILITY)).isEqualTo(CountingToolProvider.SUCCESS_OUTPUT);
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void retryThatFailsAgainReSuspendsWithAFreshPendingRowInsteadOfSilentlyAdvancing() {
    provider.alwaysFail = true;
    DefaultToolExecutionService toolService = toolService(allow());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);
    WorkflowState resumed =
        runtime.resolveToolDecision("run-1", command.toolInvocationId(), new ToolDecision.Retry("op-1"));

    assertThat(provider.invocations).isEqualTo(2);
    assertThat(resumed.getStatus()).isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);
    PendingToolInvocation freshPending = store.find("run-1", command.toolInvocationId());
    assertThat(freshPending).isNotNull();
    assertThat(freshPending.origin()).isEqualTo(PendingToolInvocation.Origin.EXECUTION_FAILED);
  }

  @Test
  void concurrentResumesOnTheSamePendingInvocationExecuteExactlyOnce() throws InterruptedException {
    DefaultToolExecutionService toolService = toolService(requireApproval());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));

    int threadCount = 2;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    List<ToolExecutionOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> conflicts = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      String actor = "actor-" + i;
      Thread thread = new Thread(() -> {
        awaitBarrier(barrier);
        try {
          outcomes.add(toolService.resume(
              "run-1", command.toolInvocationId(), new ApprovalDecision.Approve(actor)));
        } catch (ToolInvocationClaimLostException e) {
          conflicts.add(e);
        }
      });
      threads.add(thread);
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }

    // Exactly one of the two racing resumes reaches the provider; the loser never returns a
    // FAILED outcome (a genuine provider/tool failure) — it throws the typed conflict instead,
    // and never invokes the provider at all.
    assertThat(provider.invocations).isEqualTo(1);
    assertThat(outcomes).hasSize(1);
    assertThat(outcomes.get(0).status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
    assertThat(conflicts).hasSize(1);
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
  }

  /**
   * Runtime-level counterpart to the service-level race above: two threads call
   * {@code continueAfterToolApproval} for the same pending invocation concurrently. Before the
   * fix, the losing {@code resume()} returned a FAILED outcome that {@code continueAfterToolApproval}
   * treated as a genuine tool error, applying an error result and driving the run to completion a
   * second time on the same {@link WorkflowState} instance. The typed conflict must instead abort
   * the losing call before it touches run state at all.
   */
  @Test
  void concurrentRuntimeResumesOnTheSamePendingInvocationLeaveExactlyOneWinner()
      throws InterruptedException {
    DefaultToolExecutionService toolService = toolService(requireApproval());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);

    int threadCount = 2;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    List<WorkflowState> successes = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> conflicts = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      String actor = "actor-" + i;
      Thread thread = new Thread(() -> {
        awaitBarrier(barrier);
        try {
          successes.add(runtime.continueAfterToolApproval(
              "run-1", command.toolInvocationId(), new ApprovalDecision.Approve(actor)));
        } catch (ToolInvocationClaimLostException | IllegalArgumentException e) {
          // Both are legitimate loser outcomes: the typed claim-loss when the loser reaches the
          // store first, or the status-guard rejection when the winner's whole resume+drive
          // already completed before the loser even reached validateToolResumeStatus. Neither
          // mutates run state.
          conflicts.add(e);
        }
      });
      threads.add(thread);
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(provider.invocations).isEqualTo(1);
    assertThat(successes).hasSize(1);
    assertThat(conflicts).hasSize(1);

    WorkflowState finalState = stateRepo.findById("run-1").orElseThrow();
    // The final persisted status matches the winner's own outcome; the loser never re-suspended
    // the run or overwrote the winner's completion.
    assertThat(finalState.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(successes.get(0).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    // A run left in AWAITING_TOOL_DECISION with no pending row would be stranded; here the run
    // reached COMPLETED, and in every case a pending row is only absent once the run has moved
    // off both tool-suspension statuses.
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();

    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes.stream().filter(t -> t == WorkflowEventType.TOOL_INVOCATION_COMPLETED)
        .count()).isEqualTo(1);
    assertThat(eventTypes.stream().filter(t -> t == WorkflowEventType.RUN_COMPLETED)
        .count()).isEqualTo(1);
  }

  /**
   * {@code ToolDecision.Continue} did not participate in the atomic claim protocol: it applied an
   * error and dropped the pending row using a plain {@code remove(runId, toolInvocationId)}, keyed
   * only by id. Racing it against a {@code Retry} on the same pending (execution-failure) row must
   * now leave exactly one winner — the loser observes the typed conflict, never a silently-applied
   * second result.
   */
  @Test
  void concurrentContinueAndRetryOnAnExecutionFailureRaceLeavesExactlyOneWinner()
      throws InterruptedException {
    provider.failFirstInvocation = true;
    DefaultToolExecutionService toolService = toolService(allow());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    ToolExecutionOutcome first = toolService.execute(command, ctx(state));
    assertThat(first.status()).isEqualTo(ToolExecutionOutcome.Status.FAILED);
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);

    CyclicBarrier barrier = new CyclicBarrier(2);
    List<WorkflowState> successes = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> conflicts = Collections.synchronizedList(new ArrayList<>());
    Thread continueThread = new Thread(() -> {
      awaitBarrier(barrier);
      try {
        successes.add(runtime.resolveToolDecision(
            "run-1", command.toolInvocationId(), new ToolDecision.Continue("op-continue")));
      } catch (ToolInvocationClaimLostException | IllegalArgumentException e) {
        // Typed claim-loss, or the status-guard rejection when the winner's whole resume+drive
        // completed before this loser reached validateToolResumeStatus. Neither mutates state.
        conflicts.add(e);
      }
    });
    Thread retryThread = new Thread(() -> {
      awaitBarrier(barrier);
      try {
        successes.add(runtime.resolveToolDecision(
            "run-1", command.toolInvocationId(), new ToolDecision.Retry("op-retry")));
      } catch (ToolInvocationClaimLostException | IllegalArgumentException e) {
        // Same accepted loser outcomes as the Continue thread above.
        conflicts.add(e);
      }
    });
    continueThread.start();
    retryThread.start();
    continueThread.join();
    retryThread.join();

    assertThat(successes).hasSize(1);
    assertThat(conflicts).hasSize(1);
    // The initial execute() already invoked the provider once (and failed); the winner is either
    // Continue (no further invocation) or Retry (one more, successful) — never both.
    assertThat(provider.invocations).isBetween(1, 2);

    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes.stream().filter(t -> t == WorkflowEventType.RUN_COMPLETED).count())
        .isEqualTo(1);

    // No contradictory tool result/error: exactly one of the two possible context outcomes exists,
    // never both and never neither.
    WorkflowState finalState = stateRepo.findById("run-1").orElseThrow();
    boolean hasSuccessValue = finalState.getContextValue("tool." + CAPABILITY).isPresent();
    boolean hasErrorValue = finalState.getContextValue("tool." + CAPABILITY + ".error").isPresent();
    assertThat(hasSuccessValue ^ hasErrorValue).isTrue();
  }

  /**
   * Two concurrent {@code Continue} calls against the same pending row must not both apply the
   * error result: exactly one claims the row, the other observes the typed conflict.
   */
  @Test
  void concurrentContinueAndContinueRaceLeavesExactlyOneWinner() throws InterruptedException {
    DefaultToolExecutionService toolService = toolService(deny("nope"));
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    ToolExecutionOutcome denyOutcome = toolService.execute(command, ctx(state));
    assertThat(denyOutcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);

    CyclicBarrier barrier = new CyclicBarrier(2);
    List<WorkflowState> successes = Collections.synchronizedList(new ArrayList<>());
    List<Throwable> conflicts = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      String op = "op-" + i;
      Thread thread = new Thread(() -> {
        awaitBarrier(barrier);
        try {
          successes.add(runtime.resolveToolDecision(
              "run-1", command.toolInvocationId(), new ToolDecision.Continue(op)));
        } catch (ToolInvocationClaimLostException | IllegalArgumentException e) {
          // Typed claim-loss, or the status-guard rejection when the winner's whole resume+drive
          // completed before this loser reached validateToolResumeStatus. Neither mutates state.
          conflicts.add(e);
        }
      });
      threads.add(thread);
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(successes).hasSize(1);
    assertThat(conflicts).hasSize(1);
    assertThat(provider.invocations).isZero();
    assertThat(successes.get(0).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes.stream().filter(t -> t == WorkflowEventType.RUN_COMPLETED).count())
        .isEqualTo(1);
    // The winner's side effects genuinely applied exactly once: the denial reason landed in the
    // tool error context key and the pending row was consumed — the loser applied nothing.
    WorkflowState finalState = stateRepo.findById("run-1").orElseThrow();
    assertThat(contextValue("tool." + CAPABILITY + ".error")).isEqualTo("nope");
    assertThat(finalState.getContextValue("tool." + CAPABILITY)).isEmpty();
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
  }

  /**
   * Deterministic proof (no timing race — a store-level interceptor forces the exact interleaving)
   * that a {@code Continue} call which peeked a row before a concurrent {@code Retry} replaced it
   * cannot consume the replacement under stale authorization. The interceptor delays only the very
   * first {@code claim} attempt (the {@code Continue} call's own), letting the {@code Retry}'s own
   * peek-then-claim run to completion — including persisting its failure replacement — before the
   * blocked {@code Continue} call is allowed to attempt its own (now-stale) claim.
   */
  @Test
  void staleContinueClaimAgainstAReplacementRowFromAFailedRetryIsRejectedWithoutConsumingIt()
      throws Exception {
    // Distinct failure reasons per invocation, so the replacement row's content is genuinely
    // different from the row Continue observed — matching a real provider, whose retried failure
    // detail (or timestamp) will practically never collide with the original. A provider that always
    // fails with the exact same message under this suite's fixed clock would make the two rows
    // value-equal, which is not the scenario under test here.
    provider.alwaysFailWithDistinctReasons = true;
    ClaimDelayingStore delayingStore = new ClaimDelayingStore(store);
    DefaultToolExecutionService toolService = new DefaultToolExecutionService(
        new IntegrationToolProviderResolver(new InMemoryIntegrationRepository(), unusedFactory(),
            List.of(provider)),
        allow(), delayingStore, ToolExecutionOptions.defaults(), eventRecorder,
        new ObjectMapper(), CLOCK);
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    DefaultWorkflowRuntime runtime = new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of("wf-1", workflow())),
        stateRepo,
        stepSequenceExecutor,
        eventRecorder,
        CLOCK,
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        toolService,
        delayingStore,
        new DefaultRequirementResolver(),
        new TransitionGate(eventRecorder),
        RunExecutionInterceptor.NO_OP,
        new InMemoryGeneratedArtifactStore());

    AtomicReference<Throwable> continueError = new AtomicReference<>();
    Thread continueThread = new Thread(() -> {
      try {
        runtime.resolveToolDecision(
            "run-1", command.toolInvocationId(), new ToolDecision.Continue("op-continue"));
      } catch (Throwable t) {
        continueError.set(t);
      }
    });
    continueThread.start();
    assertThat(delayingStore.claimAttempted.await(10, TimeUnit.SECONDS))
        .as("claim() was never attempted - the claim protocol regressed to a plain remove()")
        .isTrue();

    // While Continue is blocked inside its own (first) claim attempt, holding the pre-replacement
    // row reference, a Retry runs to completion: it fails again and persists a replacement pending
    // row (a fresh EXECUTION_FAILED row, different content from the one Continue observed).
    runtime.resolveToolDecision("run-1", command.toolInvocationId(), new ToolDecision.Retry("op-retry"));
    PendingToolInvocation replacement = store.find("run-1", command.toolInvocationId());
    assertThat(replacement).isNotNull();
    assertThat(replacement.origin()).isEqualTo(PendingToolInvocation.Origin.EXECUTION_FAILED);

    delayingStore.proceed.countDown();
    continueThread.join();

    assertThat(continueError.get()).isInstanceOf(ToolInvocationClaimLostException.class);
    // The replacement must remain exactly as Retry left it — Continue's stale claim attempt must
    // not have consumed or otherwise altered it — so a later legitimate decision can still resolve it.
    assertThat(store.find("run-1", command.toolInvocationId())).isSameAs(replacement);
  }

  /**
   * L2-01 regression: an operator {@code Approve} whose resumed call then fails must NOT advance
   * the run. The service has already persisted a fresh {@code EXECUTION_FAILED} pending row, so
   * {@code continueAfterToolApproval} must re-suspend the run in {@code AWAITING_TOOL_DECISION} —
   * the exact status that row resolves — leaving it fully resolvable, never orphaned against a run
   * that recorded the error and moved on (where a later direct SPI {@code resume(Approve)} could
   * still claim it and re-invoke the provider).
   */
  @Test
  void approvedInvocationWhoseCallFailsReSuspendsInAwaitingToolDecisionWithTheFreshRow() {
    provider.alwaysFail = true;
    DefaultToolExecutionService toolService = toolService(requireApproval());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);
    WorkflowState resumed = runtime.continueAfterToolApproval(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice"));

    assertThat(provider.invocations).isEqualTo(1);
    assertThat(resumed.getStatus()).isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_TOOL_DECISION);
    PendingToolInvocation fresh = store.find("run-1", command.toolInvocationId());
    assertThat(fresh).isNotNull();
    assertThat(fresh.origin()).isEqualTo(PendingToolInvocation.Origin.EXECUTION_FAILED);
    // Exactly the one fresh row — nothing orphaned or duplicated for this run.
    assertThat(store.findByRun("run-1")).hasSize(1);
    // The step was not advanced and no tool error was applied yet — the operator decides first.
    assertThat(stateRepo.findById("run-1").orElseThrow()
        .getContextValue("tool." + CAPABILITY + ".error")).isEmpty();

    // The fresh row is genuinely resolvable: the operator's Continue applies the recorded failure
    // and the run completes — proving the row was never orphaned.
    WorkflowState resolved = runtime.resolveToolDecision(
        "run-1", command.toolInvocationId(), new ToolDecision.Continue("op-1"));
    assertThat(resolved.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(store.find("run-1", command.toolInvocationId())).isNull();
    assertThat(contextValue("tool." + CAPABILITY + ".error")).isEqualTo("boom");
  }

  /**
   * L2-02 regression (re-suspend sibling): a {@code cancel()} acknowledged while the retried
   * provider call is still in flight — a window spanning the whole invocation — must not be
   * clobbered back to {@code AWAITING_TOOL_DECISION} by the Retry-failed re-suspend write.
   * Cancellation always wins; the interleaving is deterministic (the provider itself cancels the
   * run mid-invocation).
   */
  @Test
  void cancelDuringAnInFlightRetriedCallWinsOverTheReSuspendWrite() {
    provider.alwaysFail = true;
    DefaultToolExecutionService toolService = toolService(allow());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_DECISION);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService);
    provider.onInvoke = () -> runtime.cancel("run-1", "canceller");

    WorkflowState resumed = runtime.resolveToolDecision(
        "run-1", command.toolInvocationId(), new ToolDecision.Retry("op-1"));

    assertThat(resumed.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.CANCELLED);
    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes.stream().filter(t -> t == WorkflowEventType.RUN_CANCELLED).count())
        .isEqualTo(1);
  }

  /**
   * L2-02 regression (handleRejected sibling): a step rejection's {@code FAILED} write racing a
   * {@code cancel()} must not overwrite the already-won {@code CANCELLED} terminal status —
   * terminal transitions are mutually exclusive and whichever wins first is final. The
   * interleaving is forced deterministically: the hooked event log fires {@code cancel()} exactly
   * between {@code decideStepApproval}'s cancellation guard (already passed) and
   * {@code handleRejected}'s terminal write — the {@code STEP_REJECTED} audit append sits between
   * the two.
   */
  @Test
  void stepRejectionRacingACancelDoesNotOverwriteCancelledWithFailed() {
    AtomicReference<DefaultWorkflowRuntime> runtimeRef = new AtomicReference<>();
    AtomicBoolean cancelFired = new AtomicBoolean(false);
    WorkflowEventLog hookedLog = new WorkflowEventLog() {
      @Override
      public void append(WorkflowEvent event) {
        eventLog.append(event);
        if (event.eventType() == WorkflowEventType.STEP_REJECTED
            && cancelFired.compareAndSet(false, true)) {
          runtimeRef.get().cancel("run-1", "canceller");
        }
      }

      @Override
      public List<WorkflowEvent> getEvents(String runId) {
        return eventLog.getEvents(runId);
      }
    };
    EventRecorder hookedRecorder = new EventRecorder(hookedLog, CLOCK);
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    DefaultWorkflowRuntime runtime = new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of("wf-1", workflow())),
        stateRepo,
        stepSequenceExecutor,
        hookedRecorder,
        CLOCK,
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        toolService(allow()),
        store,
        new DefaultRequirementResolver(),
        new TransitionGate(hookedRecorder),
        RunExecutionInterceptor.NO_OP,
        new InMemoryGeneratedArtifactStore());
    runtimeRef.set(runtime);

    WorkflowState state = seedState();
    state.setStatus(WorkflowStatus.AWAITING_STEP_APPROVAL);
    stateRepo.save(state);

    runtime.decideStepApproval("run-1", "s1",
        new com.agentforge4j.core.runtime.StepApprovalDecision.Reject("bob", "no"));

    WorkflowState finalState = stateRepo.findById("run-1").orElseThrow();
    assertThat(finalState.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    // The losing FAILED transition mutated nothing — no failure detail was recorded either.
    assertThat(finalState.getFailureReason()).isNull();
    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes).contains(
        WorkflowEventType.STEP_REJECTED, WorkflowEventType.RUN_CANCELLED);
  }

  /**
   * L2-02/L4-01 regression (transition-gate sibling): a {@code cancel()} acknowledged during the
   * approved tool call must win — the resume verb now rejects at its cancellation-aware RUNNING
   * transition instead of proceeding into {@code gateCompletedStep}'s suspension save (or letting a
   * later drive record a terminal lifecycle event after {@code RUN_CANCELLED}). The run must end
   * {@code CANCELLED} with {@code RUN_CANCELLED} as its only lifecycle event.
   */
  @Test
  void cancelDuringAnApprovedToolCallWinsOverTheTransitionGateSuspension() {
    DefaultToolExecutionService toolService = toolService(requireApproval());
    WorkflowState state = seedState();
    ToolInvocationCommand command = command();
    toolService.execute(command, ctx(state));
    state.setStatus(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    stateRepo.save(state);

    DefaultWorkflowRuntime runtime = runtime(toolService, gatedWorkflow());
    provider.onInvoke = () -> runtime.cancel("run-1", "canceller");

    assertThatThrownBy(() -> runtime.continueAfterToolApproval(
        "run-1", command.toolInvocationId(), new ApprovalDecision.Approve("alice")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CANCELLED");

    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.CANCELLED);
    List<WorkflowEventType> eventTypes = eventLog.getEvents("run-1").stream()
        .map(WorkflowEvent::eventType)
        .toList();
    assertThat(eventTypes).containsOnlyOnce(WorkflowEventType.RUN_CANCELLED);
    assertThat(eventTypes).doesNotContain(
        WorkflowEventType.RUN_COMPLETED, WorkflowEventType.RUN_FAILED,
        WorkflowEventType.RUN_BLOCKED);
  }

  /**
   * Delegates every call to the wrapped store, except that the very first {@link #claim} call
   * blocks (after signalling {@link #claimAttempted}) until {@link #proceed} is counted down —
   * deterministically forcing a claim attempt to be stale by the time it actually runs, without any
   * timing-dependent race.
   */
  private static final class ClaimDelayingStore implements PendingToolInvocationStore {

    private final PendingToolInvocationStore delegate;
    private final CountDownLatch claimAttempted = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final AtomicBoolean intercepted = new AtomicBoolean(false);

    ClaimDelayingStore(PendingToolInvocationStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public void save(PendingToolInvocation pending) {
      delegate.save(pending);
    }

    @Override
    public PendingToolInvocation find(String runId, String toolInvocationId) {
      return delegate.find(runId, toolInvocationId);
    }

    @Override
    public List<PendingToolInvocation> findByRun(String runId) {
      return delegate.findByRun(runId);
    }

    @Override
    public void remove(String runId, String toolInvocationId) {
      delegate.remove(runId, toolInvocationId);
    }

    @Override
    public PendingToolInvocation claim(String runId, String toolInvocationId,
        PendingToolInvocation expectedPending) {
      if (intercepted.compareAndSet(false, true)) {
        claimAttempted.countDown();
        try {
          proceed.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return delegate.claim(runId, toolInvocationId, expectedPending);
    }

    @Override
    public PendingToolInvocation verifyStillCurrent(String runId, String toolInvocationId,
        PendingToolInvocation expectedPending) {
      return delegate.verifyStillCurrent(runId, toolInvocationId, expectedPending);
    }
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (java.util.concurrent.BrokenBarrierException e) {
      throw new IllegalStateException(e);
    }
  }

  private DefaultToolExecutionService toolService(ToolPolicy policy) {
    return new DefaultToolExecutionService(
        new IntegrationToolProviderResolver(new InMemoryIntegrationRepository(),
            unusedFactory(), List.of(provider)),
        policy,
        store,
        ToolExecutionOptions.defaults(),
        eventRecorder,
        new ObjectMapper(),
        CLOCK);
  }

  /** The single pre-built provider feeds the resolver directly, so the factory is never called. */
  private static com.agentforge4j.core.spi.integration.ToolProviderFactory unusedFactory() {
    return definition -> {
      throw new AssertionError("factory must not be called for pre-built providers");
    };
  }

  private DefaultWorkflowRuntime runtime(DefaultToolExecutionService toolService) {
    return runtime(toolService, workflow());
  }

  private DefaultWorkflowRuntime runtime(DefaultToolExecutionService toolService,
      WorkflowDefinition workflow) {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of("wf-1", workflow)),
        stateRepo,
        stepSequenceExecutor,
        eventRecorder,
        CLOCK,
        RunContextManager.NO_OP,
        DefaultWorkflowRuntime.DEFAULT_MAX_NESTING_DEPTH,
        toolService,
        store,
        new DefaultRequirementResolver(),
        new TransitionGate(eventRecorder),
        RunExecutionInterceptor.NO_OP,
        new InMemoryGeneratedArtifactStore());
  }

  private WorkflowState seedState() {
    WorkflowState state =
        new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId("s1");
    state.putStepExecutionUid("s1", 7);
    state.setStatus(WorkflowStatus.RUNNING);
    stateRepo.save(state);
    return state;
  }

  private static ToolInvocationContext ctx(WorkflowState state) {
    return new ToolInvocationContext(state.getRunId(),
        String.valueOf(state.getStepExecutionUid("s1").orElseThrow()), "agent-1",
        new ToolScope(state.getWorkflowId(), state.getRunId()));
  }

  private static ToolInvocationCommand command() {
    return new ToolInvocationCommand(null, CAPABILITY, Map.of("title", "x"), "because");
  }

  private String contextValue(String key) {
    ContextValue value = stateRepo.findById("run-1").orElseThrow().getContextValue(key)
        .orElseThrow();
    return ((StringContextValue) value).value();
  }

  private static WorkflowDefinition workflow() {
    return workflow(StepTransition.AUTO);
  }

  /** Same single-step workflow, but "s1" carries a HUMAN_APPROVAL transition gate. */
  private static WorkflowDefinition gatedWorkflow() {
    return workflow(StepTransition.HUMAN_APPROVAL);
  }

  private static WorkflowDefinition workflow(StepTransition transition) {
    return new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(new StepDefinition(
            "s1", "s1",
            new ResourceBehaviour("/examples/sample.txt", "out", transition),
            ContextMapping.none(), null, null, null, null, null)), List.of());
  }

  private static ToolPolicy allow() {
    return (cmd, descriptor, cit) -> new PolicyDecision.Allow();
  }

  private static ToolPolicy deny(String reason) {
    return (cmd, descriptor, cit) -> new PolicyDecision.Deny(reason);
  }

  private static ToolPolicy requireApproval() {
    return (cmd, descriptor, cit) -> new PolicyDecision.RequireApproval("needs review", "OPERATOR");
  }

  private static final class CountingToolProvider implements ToolProvider {

    private static final String SCHEMA = "{\"type\":\"object\"}";
    private static final String SUCCESS_OUTPUT = "{\"ok\":true}";

    private int invocations;
    private boolean failFirstInvocation;
    private boolean alwaysFail;
    private boolean alwaysFailWithDistinctReasons;
    /** Optional hook run at the start of every invocation (set after any setup execute() calls). */
    private Runnable onInvoke;

    @Override
    public String providerId() {
      return "test:counting";
    }

    @Override
    public List<ToolDescriptor> listTools() {
      return List.of(new ToolDescriptor(CAPABILITY, "Create PR", null, SCHEMA, null,
          new ToolSource("test:counting", "create_pull_request", ToolSourceKind.REMOTE_HTTP),
          ToolRiskMetadata.conservative()));
    }

    @Override
    public ToolResult invoke(ToolDescriptor descriptor, String arguments,
        ToolInvocationContext ctx, ToolExecutionOptions options) {
      invocations++;
      if (onInvoke != null) {
        onInvoke.run();
      }
      if (alwaysFailWithDistinctReasons) {
        return ToolResult.failure("boom-" + invocations, 1L);
      }
      if (alwaysFail || (failFirstInvocation && invocations == 1)) {
        return ToolResult.failure("boom", 1L);
      }
      return ToolResult.success(SUCCESS_OUTPUT, 1L);
    }

    @Override
    public HealthStatus health() {
      return new HealthStatus(HealthStatus.State.UP, null);
    }
  }
}
