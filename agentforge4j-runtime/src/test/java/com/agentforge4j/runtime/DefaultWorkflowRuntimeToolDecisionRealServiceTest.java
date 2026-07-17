// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
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
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.Test;

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
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of("wf-1", workflow())),
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
    return new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(new StepDefinition(
            "s1", "s1",
            new ResourceBehaviour("/examples/sample.txt", "out", StepTransition.AUTO),
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
