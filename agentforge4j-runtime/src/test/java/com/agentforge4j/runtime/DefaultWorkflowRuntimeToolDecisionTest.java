// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.ApprovalDecision;
import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.core.spi.tool.ToolDecision;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolExecutionService;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
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
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.agentforge4j.runtime.tool.InMemoryPendingToolInvocationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultWorkflowRuntimeToolDecisionTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String CAPABILITY = "github.create_pull_request";

  private final InMemoryWorkflowStateRepository stateRepo = new InMemoryWorkflowStateRepository();
  private final InMemoryPendingToolInvocationStore store = new InMemoryPendingToolInvocationStore();
  private final StepSequenceExecutor stepSequenceExecutor = mock(StepSequenceExecutor.class);
  private final StubToolService toolService = new StubToolService(store);

  @Test
  void resolveToolDecisionContinueWritesErrorAndRemovesPending() {
    seedState(WorkflowStatus.AWAITING_TOOL_DECISION);
    seedPending("policy denied");

    runtime().resolveToolDecision("run-1", "tid-1", new ToolDecision.Continue("op-1"));

    assertThat(errorValue()).isEqualTo("policy denied");
    assertThat(store.find("run-1", "tid-1")).isNull();
  }

  @Test
  void resolveToolDecisionRetryAppliesResultAndRemovesPending() {
    seedState(WorkflowStatus.AWAITING_TOOL_DECISION);
    seedPending("invoke failed");
    toolService.resumeOutcome = ToolExecutionOutcome.executed(ToolResult.success("PR-ok", 1L));

    runtime().resolveToolDecision("run-1", "tid-1", new ToolDecision.Retry("op-1"));

    assertThat(contextValue("tool." + CAPABILITY)).isEqualTo("PR-ok");
    assertThat(store.find("run-1", "tid-1")).isNull();
  }

  @Test
  void resolveToolDecisionGatesAHumanReviewStepAfterAdvancing() {
    DefaultWorkflowRuntime runtime = runtimeWith(StepTransition.HUMAN_REVIEW);
    seedState(WorkflowStatus.AWAITING_TOOL_DECISION);
    seedPending("policy denied");

    runtime.resolveToolDecision("run-1", "tid-1", new ToolDecision.Continue("op-1"));

    assertThat(stateRepo.findById("run-1").orElseThrow().getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_REVIEW);
  }

  @Test
  void approveOnAwaitingToolDecisionIsRejected() {
    seedState(WorkflowStatus.AWAITING_TOOL_DECISION);

    assertThatThrownBy(() -> runtime().approve("run-1", "s1", "note", "op-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("resolveToolDecision");
  }

  @Test
  void resolveToolDecisionOnAwaitingApprovalIsRejected() {
    seedState(WorkflowStatus.AWAITING_APPROVAL);

    assertThatThrownBy(
        () -> runtime().resolveToolDecision("run-1", "tid-1", new ToolDecision.Continue("op-1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("approve");
  }

  @Test
  void continueAfterToolApprovalApproveAppliesResult() {
    seedState(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    seedPending("needs review");
    toolService.resumeOutcome = ToolExecutionOutcome.executed(
        ToolResult.success("approved-result", 1L));

    runtime().continueAfterToolApproval("run-1", "tid-1", new ApprovalDecision.Approve("alice"));

    assertThat(contextValue("tool." + CAPABILITY)).isEqualTo("approved-result");
    assertThat(store.find("run-1", "tid-1")).isNull();
  }

  @Test
  void continueAfterToolApprovalRejectWritesError() {
    seedState(WorkflowStatus.AWAITING_TOOL_APPROVAL);
    seedPending("needs review");
    toolService.resumeOutcome = ToolExecutionOutcome.denied("rejected by bob");

    runtime().continueAfterToolApproval("run-1", "tid-1",
        new ApprovalDecision.Reject("bob", "no thanks"));

    assertThat(errorValue()).isEqualTo("rejected by bob");
    assertThat(store.find("run-1", "tid-1")).isNull();
  }

  @Test
  void approveOnAwaitingToolApprovalIsRejected() {
    seedState(WorkflowStatus.AWAITING_TOOL_APPROVAL);

    assertThatThrownBy(() -> runtime().approve("run-1", "s1", "note", "op-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("continueAfterToolApproval");
  }

  @Test
  void continueAfterToolApprovalOnAwaitingApprovalIsRejected() {
    seedState(WorkflowStatus.AWAITING_APPROVAL);

    assertThatThrownBy(() -> runtime().continueAfterToolApproval("run-1", "tid-1",
        new ApprovalDecision.Approve("alice")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("approve");
  }

  @Test
  void continueAfterToolApprovalOnAwaitingToolDecisionIsRejected() {
    seedState(WorkflowStatus.AWAITING_TOOL_DECISION);

    assertThatThrownBy(() -> runtime().continueAfterToolApproval("run-1", "tid-1",
        new ApprovalDecision.Approve("alice")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("resolveToolDecision");
  }

  @Test
  void resolveToolDecisionOnAwaitingToolApprovalIsRejected() {
    seedState(WorkflowStatus.AWAITING_TOOL_APPROVAL);

    assertThatThrownBy(
        () -> runtime().resolveToolDecision("run-1", "tid-1", new ToolDecision.Continue("op-1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("continueAfterToolApproval");
  }

  private DefaultWorkflowRuntime runtime() {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
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
        com.agentforge4j.runtime.interceptor.RunExecutionInterceptor.NO_OP);
  }

  private void seedState(WorkflowStatus status) {
    WorkflowState state =
        new WorkflowState("run-1", "wf-1", null, Instant.parse("2026-05-01T00:00:00Z"));
    state.setCurrentStepId("s1");
    state.putStepExecutionUid("s1", 7);
    state.setStatus(status);
    stateRepo.save(state);
  }

  private void seedPending(String reason) {
    store.save(new PendingToolInvocation("tid-1", "run-1", "s1", "agent-1", "wf-1", CAPABILITY,
        "{}", "because", reason, null, Instant.parse("2026-05-01T00:00:00Z")));
  }

  private String contextValue(String key) {
    ContextValue value = stateRepo.findById("run-1").orElseThrow().getContextValue(key)
        .orElseThrow();
    return ((StringContextValue) value).value();
  }

  private String errorValue() {
    return contextValue("tool." + CAPABILITY + ".error");
  }

  private static WorkflowDefinition workflow() {
    return workflowWith(StepTransition.AUTO);
  }

  private static WorkflowDefinition workflowWith(StepTransition transition) {
    return new WorkflowDefinition(
        "wf-1", "wf-1", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(new StepDefinition(
            "s1", "s1",
            new ResourceBehaviour("/examples/sample.txt", "out", transition),
            ContextMapping.none(), null, null, null)));
  }

  private DefaultWorkflowRuntime runtimeWith(StepTransition transition) {
    when(stepSequenceExecutor.executeAll(anyList(), any())).thenReturn(ExecutionOutcome.COMPLETED);
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(), CLOCK);
    return new DefaultWorkflowRuntime(
        new InMemoryWorkflowRepository(Map.of("wf-1", workflowWith(transition))),
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
        com.agentforge4j.runtime.interceptor.RunExecutionInterceptor.NO_OP);
  }

  private static final class StubToolService implements ToolExecutionService {

    private final PendingToolInvocationStore store;
    private ToolExecutionOutcome resumeOutcome = ToolExecutionOutcome.denied("unset");

    private StubToolService(PendingToolInvocationStore store) {
      this.store = store;
    }

    @Override
    public ToolExecutionOutcome execute(ToolInvocationCommand cmd, ToolInvocationContext ctx) {
      throw new UnsupportedOperationException("not used in resume tests");
    }

    @Override
    public ToolExecutionOutcome resume(String runId, String toolInvocationId,
        ApprovalDecision decision) {
      // Mirror the real service: remove the pending row on any terminal outcome.
      store.remove(runId, toolInvocationId);
      return resumeOutcome;
    }
  }
}
