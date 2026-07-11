// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.command.EscalateCommand;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the {@code approve} suspended-step identity guard: an escalation approval must name the step
 * the run is actually suspended on, so the {@code APPROVED} audit event can never be attributed to an arbitrary
 * caller-supplied step id. Mirrors the protection {@code submitReview}/{@code decideStepApproval} already enforce.
 */
class ApproveSuspendedStepIdentityRuntimeTest {

  @Test
  void approve_with_wrong_step_id_is_rejected_without_resuming_or_recording() {
    Fixture fixture = fixture();
    String runId = fixture.runtime().start("wf-approve-identity");
    assertThat(fixture.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_APPROVAL);

    assertThatThrownBy(() -> fixture.runtime().approve(runId, "ghost", "note", "approver"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not suspended on step 'ghost'");

    // The run stays suspended and no APPROVED event was misattributed.
    assertThat(fixture.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
    assertThat(countApproved(fixture, runId)).isZero();
  }

  @Test
  void approve_with_suspended_step_id_resumes_and_attributes_the_event() {
    Fixture fixture = fixture();
    String runId = fixture.runtime().start("wf-approve-identity");
    assertThat(fixture.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_APPROVAL);

    fixture.runtime().approve(runId, "a1", "looks fine", "approver");

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    List<WorkflowEvent> approved = fixture.eventLog().getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.APPROVED)
        .toList();
    assertThat(approved).hasSize(1);
    assertThat(approved.get(0).stepId()).isEqualTo("a1");
  }

  private static long countApproved(Fixture fixture, String runId) {
    return fixture.eventLog().getEvents(runId).stream()
        .filter(event -> event.eventType() == WorkflowEventType.APPROVED)
        .count();
  }

  private static Fixture fixture() {
    StepDefinition a1 = StepDefinition.builder()
        .withStepId("a1")
        .withName("a1")
        .withBehaviour(new AgentBehaviour("a1-agent", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-approve-identity",
        "wf-approve-identity",
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        Map.of(),
        List.of(a1), List.of(), List.of());

    InMemoryWorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    // First invocation escalates (suspending the run); the post-approval re-invocation continues.
    AgentInvoker invoker = mock(AgentInvoker.class);
    AgentInvocationResult escalate = AgentInvocationResult.builder()
        .withRawResponse("needs a human")
        .withCommands(List.of(new EscalateCommand("needs a human")))
        .build();
    AgentInvocationResult proceed = AgentInvocationResult.builder()
        .withRawResponse("carrying on")
        .withCommands(List.of(new ContinueCommand(null, null, null)))
        .build();
    when(invoker.invoke(any(), any(), any(), any(), any(), any())).thenReturn(escalate, proceed);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(invoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, eventLog);
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowEventLog eventLog) {

  }
}
