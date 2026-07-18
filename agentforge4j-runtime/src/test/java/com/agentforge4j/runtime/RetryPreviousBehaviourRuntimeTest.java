// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryMode;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.retry.RetryPolicy;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.execution.RetryPolicyAttemptCounter;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage: a {@code RETRY_PREVIOUS} step re-fired on every resume re-drive,
 * burning another attempt and wiping already-satisfied downstream state, because the handler wrote no
 * step output for its own owning step (so {@code StepSequenceExecutor}'s resume-skip guard never
 * recognised it as done). Driven through the real runtime (start / pause / submitInput), not a
 * unit-mocked executor, so the resume-skip guard is genuinely exercised.
 */
class RetryPreviousBehaviourRuntimeTest {

  @Test
  void retry_previous_does_not_refire_on_a_downstream_resume() {
    // [A(agent), R(RETRY_PREVIOUS targeting A), B(INPUT)].
    StepDefinition a = StepDefinition.builder()
        .withStepId("a")
        .withName("a")
        .withBehaviour(new AgentBehaviour("a-agent", StepTransition.AUTO,
            // allowRetryFromPrevious=true: "a" is the RETRY_PREVIOUS target below, and the handler
            // gates RETRY_PREVIOUS on this flag when the target carries a RetryPolicy.
            new RetryPolicy(false, true, 1)))
        .withContextMapping(ContextMapping.none())
        .build();
    StepDefinition fallback = StepDefinition.builder()
        .withStepId("fallback")
        .withName("fallback")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "fallback.out",
            StepTransition.AUTO))
        .build();
    StepDefinition r = StepDefinition.builder()
        .withStepId("r")
        .withName("r")
        .withBehaviour(new RetryPreviousBehaviour("a", RetryMode.SINGLE_STEP, 3, fallback))
        .build();
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition b = StepDefinition.builder()
        .withStepId("b")
        .withName("b")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-retry-previous-resume", "wf-retry-previous-resume", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form1", form), Map.of(),
        List.of(a, r, b), List.of());

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // Drive 1: a executes, r fires attempt 1 (replays "a" a second time), b pauses awaiting input.
    WorkflowState pausedState = fixture.runtime().getState(runId);
    assertThat(pausedState.getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
    assertThat(countEvents(fixture, runId, "b", WorkflowEventType.AWAITING_INPUT)).isEqualTo(1);
    ContextValue attemptsBeforeResume = pausedState.getContext().get("__retry_previous_attempts:a");
    assertThat(attemptsBeforeResume).isNotNull();
    assertThat(((StringContextValue) attemptsBeforeResume).value()).isEqualTo("1");

    fixture.runtime().submitInput(runId, Map.of("field1", "the answer"), "user");

    // The resume must not re-fire "r": "a" is not invoked again, no further retry attempt is
    // consumed, and "b" is prompted exactly once (never re-asked).
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
    assertThat(countEvents(fixture, runId, "b", WorkflowEventType.AWAITING_INPUT)).isEqualTo(1);
    assertThat(((StringContextValue) after.getContext().get("__retry_previous_attempts:a")).value())
        .isEqualTo("1");
  }

  /**
   * Full-runtime proof that the completion marker's uid is allocated strictly after the replayed
   * target's own fresh uid, so a later external rewind that reaches the target also reaches the
   * marker: [a(agent), r(RETRY_PREVIOUS targeting a), b(INPUT), fail(FAIL)]. Drive 1 fires "r" once
   * (replaying "a") and pauses on "b"; resuming "b" must not re-fire "r" (its marker survives the
   * resume) before the run fails at "fail". An external {@code retry(runId, "a", ...)} then rewinds
   * from "a"'s current position — clearing everything at or after its uid, including "r"'s marker —
   * so "r" fires again on the re-drive instead of being permanently skipped, and every uid allocated
   * in that re-drive stays monotonically increasing.
   */
  @Test
  void external_retry_from_the_replayed_target_clears_the_completion_marker_and_r_fires_again() {
    StepDefinition a = StepDefinition.builder()
        .withStepId("a")
        .withName("a")
        .withBehaviour(new AgentBehaviour("a-agent", StepTransition.AUTO,
            new RetryPolicy(true, true, 5)))
        .withContextMapping(ContextMapping.none())
        .build();
    StepDefinition fallback = StepDefinition.builder()
        .withStepId("fallback")
        .withName("fallback")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "fallback.out",
            StepTransition.AUTO))
        .build();
    StepDefinition r = StepDefinition.builder()
        .withStepId("r")
        .withName("r")
        .withBehaviour(new RetryPreviousBehaviour("a", RetryMode.SINGLE_STEP, 3, fallback))
        .build();
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition b = StepDefinition.builder()
        .withStepId("b")
        .withName("b")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    StepDefinition fail = StepDefinition.builder()
        .withStepId("fail")
        .withName("fail")
        .withBehaviour(new FailBehaviour("expected"))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-retry-previous-external-rewind", "wf-retry-previous-external-rewind", null, null, null,
        null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form1", form),
        Map.of(), List.of(a, r, b, fail), List.of());

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // Drive 1: a executes, r fires attempt 1 (replays a), b pauses awaiting input.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(2);

    fixture.runtime().submitInput(runId, Map.of("field1", "the answer"), "user");

    // The resume must not re-fire "r" (its marker survives), so "a" is not invoked a third time
    // before the run fails at the terminal FAIL step.
    WorkflowState failed = fixture.runtime().getState(runId);
    assertThat(failed.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
    assertThat(countEvents(fixture, runId, "b", WorkflowEventType.AWAITING_INPUT)).isEqualTo(1);

    // External rewind targeting "a" directly: clears everything at or after a's current uid,
    // including r's completion marker (its uid, allocated strictly after a's replay, is higher).
    fixture.runtime().retry(runId, "a", "operator");

    // "r"'s marker having been cleared, the re-drive re-enters and re-fires it — replaying "a" a
    // third time overall — rather than skipping straight past to a stale downstream state.
    WorkflowState afterRetry = fixture.runtime().getState(runId);
    assertThat(afterRetry.getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
    assertThat(countEvents(fixture, runId, "b", WorkflowEventType.AWAITING_INPUT)).isEqualTo(2);
    assertThat(afterRetry.getStepOutputs()).containsKey("r");

    // UID ordering within the re-drive stays monotonic: a's own replay uid precedes r's marker uid,
    // which precedes b's uid.
    int aUid = afterRetry.getStepExecutionUid().get("a");
    int rUid = afterRetry.getStepExecutionUid().get("r");
    int bUid = afterRetry.getStepExecutionUid().get("b");
    assertThat(aUid).isLessThan(rUid);
    assertThat(rUid).isLessThan(bUid);
  }

  /**
   * L2-03 regression (FROM_STEP): a pause <em>inside</em> the replay range must resume the same
   * dispatch on re-entry — consuming no further local attempt and no further shared-ceiling
   * reservation, and never re-clearing the state the pause's resolution just satisfied. The shared
   * {@code RetryPolicy} ceiling is deliberately 1: before the fix, the re-entry restarted the
   * dispatch and re-reserved the exhausted ceiling, failing the run outright where the author had
   * supplied a fallback — and with a larger ceiling it silently burned two budget units per
   * logical retry while wiping the just-submitted input and re-prompting.
   */
  @Test
  void pause_inside_the_replay_range_resumes_the_same_dispatch_without_burning_further_attempts() {
    // [a(agent, RetryPolicy maxAttempts=1), inp(INPUT), r(FROM_STEP targeting a)].
    StepDefinition a = StepDefinition.builder()
        .withStepId("a")
        .withName("a")
        .withBehaviour(new AgentBehaviour("a-agent", StepTransition.AUTO,
            new RetryPolicy(false, true, 1)))
        .withContextMapping(ContextMapping.none())
        .build();
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition inp = StepDefinition.builder()
        .withStepId("inp")
        .withName("inp")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    StepDefinition fallback = StepDefinition.builder()
        .withStepId("fallback")
        .withName("fallback")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "fallback.out",
            StepTransition.AUTO))
        .build();
    StepDefinition r = StepDefinition.builder()
        .withStepId("r")
        .withName("r")
        .withBehaviour(new RetryPreviousBehaviour("a", RetryMode.FROM_STEP, 2, fallback))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-retry-previous-in-range-pause", "wf-retry-previous-in-range-pause", null, null, null,
        null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form1", form),
        Map.of(), List.of(a, inp, r), List.of());

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // Drive 1: a executes, inp pauses — r has not fired yet.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(1);
    assertThat(countEvents(fixture, runId, "inp", WorkflowEventType.AWAITING_INPUT)).isEqualTo(1);

    // Resume 1: r fires its dispatch — reserves the single shared attempt, records one local
    // attempt, replays a, and pauses again on the replayed inp (the pause is INSIDE the range).
    fixture.runtime().submitInput(runId, Map.of("field1", "first answer"), "user");
    WorkflowState midDispatch = fixture.runtime().getState(runId);
    assertThat(midDispatch.getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
    assertThat(countEvents(fixture, runId, "inp", WorkflowEventType.AWAITING_INPUT)).isEqualTo(2);
    assertThat(((StringContextValue) midDispatch.getContext().get("__retry_previous_attempts:a")).value())
        .isEqualTo("1");
    assertThat(((StringContextValue) midDispatch.getContext().get("__retry_policy_attempts:a")).value())
        .isEqualTo("1");

    // Resume 2: the re-entered r must CONTINUE the interrupted dispatch — the run completes; the
    // exhausted shared ceiling is not re-reserved (before the fix: StepExecutionException →
    // FAILED), no further attempt is recorded, the answer just submitted survives, and inp is
    // never re-prompted.
    fixture.runtime().submitInput(runId, Map.of("field1", "second answer"), "user");
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(fixture, runId, "a", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
    assertThat(countEvents(fixture, runId, "inp", WorkflowEventType.AWAITING_INPUT)).isEqualTo(2);
    assertThat(((StringContextValue) after.getContext().get("__retry_previous_attempts:a")).value())
        .isEqualTo("1");
    assertThat(((StringContextValue) after.getContext().get("__retry_policy_attempts:a")).value())
        .isEqualTo("1");
    assertThat(((StringContextValue) after.getContext().get("form1.field1")).value())
        .isEqualTo("second answer");
    // The dispatch genuinely completed: its in-flight marker is cleared and r bears its marker.
    assertThat(after.getContext().get("__retry_previous_inflight:r")).isNull();
    assertThat(after.getStepOutputs()).containsKey("r");
  }

  /**
   * L2-03 regression (SINGLE_STEP): the replayed target itself pausing must likewise resume the
   * same dispatch on re-entry instead of re-clearing and re-prompting.
   */
  @Test
  void pause_of_the_single_step_replay_target_resumes_the_same_dispatch() {
    ArtifactDefinition form = new ArtifactDefinition("form1",
        List.of(new TextArtifactItem("field1", "Field", true, null)));
    StepDefinition inp = StepDefinition.builder()
        .withStepId("inp")
        .withName("inp")
        .withBehaviour(new InputBehaviour("form1", StepTransition.AUTO))
        .build();
    StepDefinition fallback = StepDefinition.builder()
        .withStepId("fallback")
        .withName("fallback")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "fallback.out",
            StepTransition.AUTO))
        .build();
    StepDefinition r = StepDefinition.builder()
        .withStepId("r")
        .withName("r")
        .withBehaviour(new RetryPreviousBehaviour("inp", RetryMode.SINGLE_STEP, 3, fallback))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-retry-previous-single-step-pause", "wf-retry-previous-single-step-pause", null, null,
        null, null, null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of("form1", form),
        Map.of(), List.of(inp, r), List.of());

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);

    // Resume 1: r fires, clears the target, replays inp — which pauses again (second prompt).
    fixture.runtime().submitInput(runId, Map.of("field1", "first answer"), "user");
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_INPUT);
    assertThat(countEvents(fixture, runId, "inp", WorkflowEventType.AWAITING_INPUT)).isEqualTo(2);

    // Resume 2: the re-entered r continues the same dispatch — completed, one attempt, no third
    // prompt, the second answer intact.
    fixture.runtime().submitInput(runId, Map.of("field1", "second answer"), "user");
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(fixture, runId, "inp", WorkflowEventType.AWAITING_INPUT)).isEqualTo(2);
    assertThat(((StringContextValue) after.getContext().get("__retry_previous_attempts:inp")).value())
        .isEqualTo("1");
    assertThat(((StringContextValue) after.getContext().get("form1.field1")).value())
        .isEqualTo("second answer");
  }

  /**
   * L2-09 regression: the local RETRY_PREVIOUS counter of a step literally named {@code policy_x}
   * must not alias the shared {@code RetryPolicy} counter of a step named {@code x}. Under the old
   * {@code __retry_<id>_attempts} key shape both resolved to {@code __retry_policy_x_attempts};
   * the prefix-free key layout keeps the two families disjoint for every possible step id.
   */
  @Test
  void local_counter_of_a_policy_prefixed_step_does_not_alias_the_shared_policy_counter() {
    StepDefinition policyX = StepDefinition.builder()
        .withStepId("policy_x")
        .withName("policy_x")
        .withBehaviour(new AgentBehaviour("a-agent", StepTransition.AUTO,
            new RetryPolicy(false, true, 5)))
        .withContextMapping(ContextMapping.none())
        .build();
    StepDefinition fallback = StepDefinition.builder()
        .withStepId("fallback")
        .withName("fallback")
        .withBehaviour(new ResourceBehaviour("/workflow-resources/info.txt", "fallback.out",
            StepTransition.AUTO))
        .build();
    StepDefinition r = StepDefinition.builder()
        .withStepId("r")
        .withName("r")
        .withBehaviour(new RetryPreviousBehaviour("policy_x", RetryMode.SINGLE_STEP, 3, fallback))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition(
        "wf-retry-previous-key-alias", "wf-retry-previous-key-alias", null, null, null, null, null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(policyX, r), List.of());

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    // One local attempt for policy_x, one shared reservation for policy_x's own budget…
    assertThat(((StringContextValue) after.getContext().get("__retry_previous_attempts:policy_x")).value())
        .isEqualTo("1");
    assertThat(RetryPolicyAttemptCounter.read(after, "policy_x")).isEqualTo(1);
    // …and the shared budget of an unrelated step named "x" is untouched (the old key shape
    // reported 1 here — silent cross-talk between two unrelated budgets).
    assertThat(RetryPolicyAttemptCounter.read(after, "x")).isZero();
  }

  private static long countEvents(Fixture fixture, String runId, String stepId,
      WorkflowEventType type) {
    List<WorkflowEvent> events = fixture.eventLog().getEvents(runId);
    return events.stream()
        .filter(event -> event.eventType() == type)
        .filter(event -> stepId == null ? event.stepId() == null : stepId.equals(event.stepId()))
        .count();
  }

  private static AgentInvoker continuingAgentInvoker() {
    AgentInvoker invoker = mock(AgentInvoker.class);
    AgentInvocationResult result = AgentInvocationResult.builder()
        .withRawResponse("agent-output")
        .withCommands(List.of(new ContinueCommand(null, null, null)))
        .build();
    when(invoker.invoke(any(), any(), any(), any(), any(), any())).thenReturn(result);
    return invoker;
  }

  private static Fixture fixture(WorkflowDefinition workflow, AgentInvoker agentInvoker) {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of(workflow.id(), workflow)))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, stateRepository, eventLog);
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowStateRepository stateRepository,
                         WorkflowEventLog eventLog) {

  }
}
