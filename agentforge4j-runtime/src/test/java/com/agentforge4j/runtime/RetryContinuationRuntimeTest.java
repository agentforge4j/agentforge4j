// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.ContinueCommand;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.state.RunFailure;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ResourceBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.llm.ContextRenderer;
import com.agentforge4j.runtime.llm.FirstAvailableProviderSelectionStrategy;
import com.agentforge4j.runtime.llm.LlmCallObserver;
import com.agentforge4j.runtime.llm.LlmCommandParser;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Regression coverage for the {@code retry} continuation contract (Path A): retrying a top-level step repositions the
 * run and re-drives the enclosing sequence so the target and every downstream step execute again, instead of finalising
 * the run after running the target alone. Nested targets are rejected fail-fast.
 */
class RetryContinuationRuntimeTest {

  @Test
  void retry_middle_step_redrives_downstream_and_does_not_complete_early() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    StepDefinition s3 = resourceStep("s3", "/templates/template.txt", "k3");
    StepDefinition terminalFail = failStep("fail");
    WorkflowDefinition workflow = workflow("wf-retry-middle", Map.of(),
        List.of(s1, s2, s3, terminalFail));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "s2", "user");

    // The downstream terminal fail step runs again, so the run fails again rather than completing
    // after executing s2 alone (the pre-fix defect finalised COMPLETED here).
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(countEvents(fixture, runId, null, WorkflowEventType.RUN_COMPLETED)).isZero();
  }

  @Test
  void retry_clears_stale_target_output_but_keeps_completed_upstream() {
    // Agent steps register a step output, so the resume replay-skip would reuse a stale output if it
    // were not cleared. This isolates the clearing: a1 (upstream of the target) is kept and skipped;
    // a2 (the target) is cleared and re-executes.
    StepDefinition a1 = agentStep("a1");
    StepDefinition a2 = agentStep("a2");
    StepDefinition terminalFail = failStep("fail");
    WorkflowDefinition workflow = workflow("wf-retry-stale", Map.of(),
        List.of(a1, a2, terminalFail));

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "a2", "user");

    assertThat(countEvents(fixture, runId, "a1", WorkflowEventType.STEP_STARTED)).isEqualTo(1);
    assertThat(countEvents(fixture, runId, "a2", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
  }

  @Test
  void retry_paused_run_clears_pending_suspension_and_redrives_to_completion() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-retry-paused", Map.of(), List.of(s1, s2));

    Fixture fixture = fixture(workflow);
    String runId = "paused-run";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.putStepOutput("s1", "stale");
    seeded.putStepExecutionUid("s1", 1);
    seeded.setCurrentStepId("s1");
    seeded.setPendingUserPrompt("waiting for a condition");
    seeded.setStatus(WorkflowStatus.PAUSED);
    fixture.stateRepository().save(seeded);

    fixture.runtime().retry(runId, "s1", "user");

    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(after.getPendingUserPrompt()).isNull();
    assertThat(after.getContext()).containsKeys("k1", "k2");
  }

  @Test
  void retry_across_a_loop_paused_at_max_iterations_restarts_it_from_iteration_one() {
    // Regression for a stale loop cursor surviving a retry rewind: an AGENT_SIGNAL loop that never
    // signals completion pauses via MaxIterationsHandler's AWAIT_USER action, which — unlike the
    // FAIL action — does not clear the loop's cursor/body-start-uid. Retrying an earlier top-level
    // step must still forget them (via WorkflowState.clearEntriesFromUid), so the loop restarts at
    // iteration 1 on the redrive instead of resuming mid-way and silently running fewer iterations.
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition body = agentStep("body");
    BlueprintDefinition loopBp = new BlueprintDefinition("loop-bp", "loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2,
                MaxIterationsAction.AWAIT_USER),
            StepTransition.AUTO),
        List.of(body));
    WorkflowDefinition workflow = workflow("wf-retry-paused-loop",
        Map.of("loop-bp", loopBp), List.of(s1, new BlueprintRef("loop-bp")));

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // The agent always CONTINUEs, so the loop never signals completion and reaches maxIterations=2,
    // pausing via AWAIT_USER with a non-zero cursor.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(2);

    fixture.runtime().retry(runId, "s1", "user");

    // Pre-fix, the stale cursor (2) survived the rewind and the redrive resumed at iteration 2,
    // running the body only once more (3 total) before re-pausing. The fix clears the cursor, so the
    // redrive restarts the loop at iteration 1 and runs both iterations again (4 total).
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
  }

  @Test
  void retry_targeting_a_step_after_a_loop_paused_at_max_iterations_still_restarts_it_from_iteration_one() {
    // Regression for retry() never rewinding an AWAIT_USER max-iterations pause when the retry
    // target's own rewind threshold does not reach the paused loop: unlike the sibling test above
    // (target "s1" lies before the loop, so the generic earliestUidAtOrAfter sweep already covers
    // it), this workflow's only step after the loop, "s2", has never executed — earliestUidAtOrAfter
    // returns null and the generic sweep never runs at all. Pre-fix, retry() performed no rewind
    // whatsoever, so the redrive resumed the loop mid-way, the resume-skip guard skipped the
    // already-recorded body, and the run silently re-paused with zero progress (body STEP_STARTED
    // stuck at 2, "s2" never reached). The fix rewinds via the same AWAIT_USER-pause helper
    // continueRun uses, independent of the target's position, so the loop restarts at iteration 1.
    StepDefinition body = agentStep("body");
    BlueprintDefinition loopBp = new BlueprintDefinition("loop-bp", "loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2,
                MaxIterationsAction.AWAIT_USER),
            StepTransition.AUTO),
        List.of(body));
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-retry-past-paused-loop",
        Map.of("loop-bp", loopBp), List.of(new BlueprintRef("loop-bp"), s2));

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // The agent always CONTINUEs, so the loop never signals completion and reaches maxIterations=2,
    // pausing via AWAIT_USER before "s2" — the only other top-level retry target — ever executes.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(2);

    fixture.runtime().retry(runId, "s2", "user");

    // Pre-fix: no rewind at all, so the redrive resumes mid-way, the body is skip-guarded, and the
    // run silently re-pauses with an unchanged body STEP_STARTED count. The fix clears the loop's
    // stale cursor unconditionally, so the redrive restarts the loop at iteration 1 and runs both
    // iterations again (4 total) before re-pausing (the agent still never signals completion).
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
  }

  @Test
  void continue_across_a_loop_paused_at_max_iterations_restarts_it_from_iteration_one() {
    // Regression for continueRun (the documented resume verb for an AWAIT_USER max-iterations
    // pause) never rewinding the loop's cursor/body-start-uid: pre-fix, the resume-skip guard
    // skipped the already-recorded body entirely and the loop re-paused with zero progress on every
    // subsequent continueRun. The fix rewinds the loop's completed iteration the same way retry
    // does, so it restarts at iteration 1 and runs both iterations again.
    StepDefinition body = agentStep("body");
    BlueprintDefinition loopBp = new BlueprintDefinition("loop-bp", "loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2,
                MaxIterationsAction.AWAIT_USER),
            StepTransition.AUTO),
        List.of(body));
    WorkflowDefinition workflow = workflow("wf-continue-paused-loop",
        Map.of("loop-bp", loopBp), List.of(new BlueprintRef("loop-bp")));

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // The agent always CONTINUEs, so the loop never signals completion and reaches maxIterations=2,
    // pausing via AWAIT_USER.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(2);

    fixture.runtime().continueRun(runId, "user");

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
  }

  @Test
  void continue_across_a_loop_paused_at_max_iterations_lets_a_later_agent_signal_reach_completion() {
    // continueRun's rewind gives the loop a genuinely fresh attempt, not a hollow re-pause: when the
    // agent scripted for the restarted attempt signals completion, the run actually completes.
    StepDefinition body = agentStep("body");
    BlueprintDefinition loopBp = new BlueprintDefinition("loop-bp", "loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2,
                MaxIterationsAction.AWAIT_USER),
            StepTransition.AUTO),
        List.of(body));
    WorkflowDefinition workflow = workflow("wf-continue-paused-loop-completes",
        Map.of("loop-bp", loopBp), List.of(new BlueprintRef("loop-bp")));

    AgentInvoker invoker = mock(AgentInvoker.class);
    AgentInvocationResult continueResult = AgentInvocationResult.builder()
        .withRawResponse("agent-output")
        .withCommands(List.of(new ContinueCommand(null, null, null)))
        .build();
    AgentInvocationResult completeResult = AgentInvocationResult.builder()
        .withRawResponse("agent-output")
        .withCommands(List.of(new CompleteCommand("done")))
        .build();
    when(invoker.invoke(any(), any(), any(), any(), any(), any()))
        .thenReturn(continueResult, continueResult, continueResult, completeResult);

    Fixture fixture = fixture(workflow, invoker);
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);

    fixture.runtime().continueRun(runId, "user");

    // The restarted attempt's second iteration signals completion, so continueRun does not merely
    // re-pause identically — the loop (and the run, since it is the workflow's only step) completes.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
  }

  @Test
  void continue_across_a_for_each_loop_paused_at_max_iterations_restarts_it_from_iteration_one() {
    // FOR_EACH has its own distinct AWAIT_USER trigger (list longer than maxIterations, unlike
    // AGENT_SIGNAL's never-signalled termination above) and its own extra resume state (the list
    // fingerprint) that the generic rewind sweep must also discard, or a restart would still be
    // misread as a resume into an already-exhausted loop. The sibling AGENT_SIGNAL tests above never
    // exercise this combination.
    StepDefinition body = resourceStep("body", "/examples/sample.txt", "body.out");
    BlueprintDefinition loopBp = new BlueprintDefinition("loop-bp", "loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.FOR_EACH, "items", null, 2,
                MaxIterationsAction.AWAIT_USER),
            StepTransition.AUTO),
        List.of(body));
    WorkflowDefinition workflow = workflow("wf-for-each-paused-loop",
        Map.of("loop-bp", loopBp), List.of(new BlueprintRef("loop-bp")));

    Fixture fixture = fixture(workflow);
    String runId = "for-each-paused-run";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.putContextValue("items", new ContextValueList(
        List.of(
            new StringContextValue("a", ContextProvenance.USER_SUPPLIED),
            new StringContextValue("b", ContextProvenance.USER_SUPPLIED),
            new StringContextValue("c", ContextProvenance.USER_SUPPLIED)),
        ContextProvenance.USER_SUPPLIED));
    seeded.setStatus(WorkflowStatus.PAUSED);
    fixture.stateRepository().save(seeded);

    // Bootstrap: the list has 3 elements but maxIterations caps the loop at 2, so this first drive
    // runs iterations 1-2 and pauses via AWAIT_USER before the loop ever reaches element "c".
    fixture.runtime().continueRun(runId, "user");

    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(2);

    fixture.runtime().continueRun(runId, "user");

    // The generic AWAIT_USER rewind sweep (added for AGENT_SIGNAL loops) must also correctly
    // restart a FOR_EACH loop: cursor, body-start-uid, and list fingerprint are all cleared, so the
    // redrive re-enters as fresh and runs both capped iterations again instead of silently
    // re-pausing with no progress.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
  }

  @Test
  void continue_across_a_nested_loop_paused_at_max_iterations_restarts_only_the_inner_loop_from_iteration_one() {
    // A loop's body can itself contain a BlueprintRef to another loop-configured blueprint, so an
    // AWAIT_USER max-iterations pause can occur while an *enclosing* loop's own iteration is still in
    // progress. The rewind sweep (WorkflowState.clearEntriesFromUid, driven from
    // rewindLoopAwaitingMaxIterationsDecision) must restart only the paused inner loop's
    // cursor/body-start-uid/fingerprint — the outer loop's own bookkeeping, still legitimately in
    // progress, must survive untouched. The outer loop's body-start-uid is always numerically lower
    // than the nested inner loop's (the inner loop starts later within the outer iteration's body),
    // so this proves the sweep's uid-threshold comparison correctly separates the two.
    StepDefinition innerBody = agentStep("inner-body");
    BlueprintDefinition innerLoopBp = new BlueprintDefinition("inner-loop-bp", "inner-loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.AGENT_SIGNAL, null, null, 2,
                MaxIterationsAction.AWAIT_USER),
            StepTransition.AUTO),
        List.of(innerBody));
    BlueprintDefinition outerLoopBp = new BlueprintDefinition("outer-loop-bp", "outer-loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.FIXED_COUNT, null, null, 1, null),
            StepTransition.AUTO),
        List.of(new BlueprintRef("inner-loop-bp")));
    WorkflowDefinition workflow = workflow("wf-nested-loop-paused",
        Map.of("outer-loop-bp", outerLoopBp, "inner-loop-bp", innerLoopBp),
        List.of(new BlueprintRef("outer-loop-bp")));

    Fixture fixture = fixture(workflow, continuingAgentInvoker());
    String runId = fixture.runtime().start(workflow.id());

    // The inner agent always CONTINUEs, so the inner loop never signals completion and reaches its
    // own maxIterations=2, pausing via AWAIT_USER — while the outer loop's single (maxIterations=1)
    // iteration is still in progress, since its body (the inner BlueprintRef) has not yet returned.
    WorkflowState pausedState = fixture.runtime().getState(runId);
    assertThat(pausedState.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "inner-body", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
    int outerCursorBeforeResume = pausedState.getLoopIterationCursor("outer-loop-bp");
    int outerBodyStartUidBeforeResume = pausedState.getLoopIterationBodyStartUid("outer-loop-bp");
    assertThat(outerCursorBeforeResume).isEqualTo(1);
    assertThat(outerBodyStartUidBeforeResume).isGreaterThan(0);

    fixture.runtime().continueRun(runId, "user");

    // The generic AWAIT_USER rewind sweep must restart only the inner loop at iteration 1 (four total
    // inner-body starts) while the outer loop's own in-progress cursor/body-start-uid survive
    // untouched — a regression here would either fail to restart the inner loop (no progress, count
    // stays 2) or wipe the outer loop's own bookkeeping (a stale-cursor bug for nested loops
    // specifically).
    WorkflowState afterResume = fixture.runtime().getState(runId);
    assertThat(afterResume.getStatus()).isEqualTo(WorkflowStatus.PAUSED);
    assertThat(countEvents(fixture, runId, "inner-body", WorkflowEventType.STEP_STARTED)).isEqualTo(4);
    assertThat(afterResume.getLoopIterationCursor("outer-loop-bp")).isEqualTo(outerCursorBeforeResume);
    assertThat(afterResume.getLoopIterationBodyStartUid("outer-loop-bp"))
        .isEqualTo(outerBodyStartUidBeforeResume);
  }

  @Test
  void retry_blueprint_inner_step_is_rejected_with_enclosing_id() {
    StepDefinition inner = resourceStep("inner", "/examples/sample.txt", "inner.result");
    BlueprintDefinition blueprint = blueprint("bp1", List.of(inner));
    WorkflowDefinition workflow = workflow("wf-retry-bp",
        Map.of(blueprint.blueprintId(), blueprint),
        List.of(new BlueprintRef(blueprint.blueprintId())));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "inner", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a top-level retry target")
        .hasMessageContaining("retry the enclosing step 'bp1'");
  }

  @Test
  void retry_loop_body_step_is_rejected_with_enclosing_id() {
    StepDefinition loopStep = resourceStep("loop-step", "/examples/sample.txt", "loop.result");
    BlueprintDefinition loopBlueprint = blueprint("loop-bp", List.of(loopStep));
    WorkflowDefinition workflow = workflow("wf-retry-loop",
        Map.of(loopBlueprint.blueprintId(), loopBlueprint),
        List.of(new BlueprintRef(loopBlueprint.blueprintId())));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "loop-step", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retry the enclosing step 'loop-bp'");
  }

  @Test
  void retry_nested_workflow_step_is_rejected_with_enclosing_id() {
    StepDefinition nestedStep = resourceStep("nested-step", "/examples/sample.txt", "nested.result");
    WorkflowDefinition nested = workflow("nested-wf", Map.of(), List.of(nestedStep));
    WorkflowDefinition workflow = workflow("wf-retry-nested", Map.of(), List.of(nested));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "nested-step", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retry the enclosing step 'nested-wf'");
  }

  @Test
  void retry_past_gated_blueprint_reruns_and_regates_the_blueprint() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition b1 = resourceStep("b1", "/workflow-resources/info.txt", "k2");
    BlueprintDefinition gated = new BlueprintDefinition("bp-gated", "bp-gated",
        new BlueprintBehaviour(null, StepTransition.HUMAN_APPROVAL), List.of(b1));
    StepDefinition terminalFail = failStep("fail");
    WorkflowDefinition workflow = workflow("wf-retry-gated-bp",
        Map.of("bp-gated", gated),
        List.of(s1, new BlueprintRef("bp-gated"), terminalFail));

    Fixture fixture = fixture(workflow);
    String runId = fixture.runtime().start(workflow.id());
    assertThat(fixture.runtime().getState(runId).getStatus())
        .isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    fixture.runtime().decideStepApproval(runId, "bp-gated",
        new StepApprovalDecision.Approve("approver", "ok"));
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "s1", "user");

    // The rewind cleared the blueprint's body state, so the re-drive must re-run and re-gate the
    // blueprint — the pre-fix defect left the gate marker in place and silently skipped the
    // blueprint over its wiped body state.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.AWAITING_STEP_APPROVAL);
    assertThat(after.getContext()).containsKey("k2");
    assertThat(countEvents(fixture, runId, "b1", WorkflowEventType.STEP_STARTED)).isEqualTo(2);
  }

  @Test
  void retry_that_completes_clears_stale_failure_details() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-retry-clears-failure", Map.of(), List.of(s1, s2));

    Fixture fixture = fixture(workflow);
    String runId = "failed-with-details";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.setStatus(WorkflowStatus.FAILED);
    seeded.setRunFailure(new RunFailure.ExceptionFailure("boom", "s1", "support-1"));
    fixture.stateRepository().save(seeded);

    fixture.runtime().retry(runId, "s1", "user");

    // The failure details belong to the discarded attempt: a retried run that completes must not
    // keep reporting the dead attempt's reason/step/supportId.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(after.getRunFailure()).isNull();
    assertThat(after.getFailureReason()).isNull();
  }

  @Test
  void continue_after_a_self_terminating_loop_already_completed_does_not_reemit_phantom_iteration_events() {
    // FIXED_COUNT/FOR_EACH loops are never marked "completed" the way signal-terminated loops are
    // (BlueprintExecutor.resolveExecutionOutcome only skip-guards AGENT_SIGNAL/EVALUATOR, since
    // FOR_EACH must keep re-checking its list for mutation), so a self-terminating loop is re-entered
    // on every later top-level redrive of the workflow. Every body step is already recorded from the
    // original completed pass, so StepSequenceExecutor's resume-skip guard skips the whole body —
    // there must be no phantom LOOP_ITERATION_STARTED/COMPLETED events for iterations that genuinely
    // executed nothing.
    StepDefinition body = resourceStep("body", "/examples/sample.txt", "body.out");
    BlueprintDefinition loopBp = new BlueprintDefinition("loop-bp", "loop-bp",
        new BlueprintBehaviour(
            LoopConfig.withDefaults(LoopTerminationStrategy.FIXED_COUNT, null, null, 2, null),
            StepTransition.AUTO),
        List.of(body));
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-completed-loop-redrive",
        Map.of("loop-bp", loopBp), List.of(new BlueprintRef("loop-bp"), s2));

    Fixture fixture = fixture(workflow);
    String runId = "completed-loop-run";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.putStepOutput("body", "done");
    seeded.putStepExecutionUid("body", 2);
    seeded.setCurrentStepId("s2");
    seeded.setPendingUserPrompt("waiting for a condition");
    seeded.setStatus(WorkflowStatus.PAUSED);
    fixture.stateRepository().save(seeded);

    fixture.runtime().continueRun(runId, "user");

    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(countEvents(fixture, runId, "loop-bp", WorkflowEventType.LOOP_ITERATION_STARTED))
        .isZero();
    assertThat(countEvents(fixture, runId, "loop-bp", WorkflowEventType.LOOP_ITERATION_COMPLETED))
        .isZero();
    assertThat(countEvents(fixture, runId, "body", WorkflowEventType.STEP_STARTED)).isZero();
  }

  @Test
  void resume_drive_allocates_uids_above_persisted_ones() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition s2 = resourceStep("s2", "/workflow-resources/info.txt", "k2");
    WorkflowDefinition workflow = workflow("wf-uid-monotonic", Map.of(), List.of(s1, s2));

    Fixture fixture = fixture(workflow);
    String runId = "paused-uid-run";
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.putStepOutput("s1", "done");
    seeded.putStepExecutionUid("s1", 5);
    seeded.setCurrentStepId("s1");
    seeded.setStatus(WorkflowStatus.PAUSED);
    fixture.stateRepository().save(seeded);

    fixture.runtime().continueRun(runId, "user");

    // s2 executed on the resume drive; its uid must continue the run's ordering. Pre-fix the
    // counter restarted at 1 on every drive, colliding below s1's uid and breaking the rewind
    // range logic that clearEntriesFromUid applies on retry.
    WorkflowState after = fixture.runtime().getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(after.getStepExecutionUid().get("s2")).isGreaterThan(5);
  }

  @Test
  void retry_of_cyclic_workflow_references_is_bounded_and_fails_cleanly() {
    // Programmatic definitions bypass load-time cycle validation, so a cyclic WORKFLOW reference
    // pair is constructible. Execution fails the run cleanly at the nesting guard; the retry rewind
    // walk must be bounded the same way rather than recursing the definition graph without limit.
    StepDefinition intoB = workflowStep("into-b", "wf-cycle-b");
    StepDefinition backToA = workflowStep("back-to-a", "wf-cycle-a");
    WorkflowDefinition wfA = workflow("wf-cycle-a", Map.of(), List.of(intoB));
    WorkflowDefinition wfB = workflow("wf-cycle-b", Map.of(), List.of(backToA));

    Fixture fixture = fixture(Map.of(wfA.id(), wfA, wfB.id(), wfB), defaultAgentInvoker());
    String runId = fixture.runtime().start(wfA.id());
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    fixture.runtime().retry(runId, "into-b", "user");

    // The retry completed normally (no StackOverflowError) and the re-drive failed again at the
    // same execution-time cycle guard.
    assertThat(fixture.runtime().getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void retry_with_a_cyclic_blueprint_reference_downstream_is_bounded() {
    // A self-referential blueprint body is likewise constructible programmatically. Execution
    // rejects it fail-fast (WorkflowTreeWalker's depth bound fires at the first drive), so a FAILED
    // run can only exist for such a definition via seeded or restored state — but the retry rewind
    // walk runs before the drive and must be bounded for it too, never overflowing the stack.
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    StepDefinition terminalFail = failStep("fail");
    BlueprintDefinition selfReferential = new BlueprintDefinition(
        "bp-self", "bp-self",
        new BlueprintBehaviour(null, StepTransition.AUTO),
        List.of(new BlueprintRef("bp-self")));
    WorkflowDefinition workflow = workflow("wf-cyclic-bp",
        Map.of("bp-self", selfReferential),
        List.of(s1, terminalFail, new BlueprintRef("bp-self")));

    Fixture fixture = fixture(workflow);
    assertThatThrownBy(() -> fixture.runtime().start(workflow.id()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("circular blueprint reference");

    String runId = seedFailedRun(fixture, workflow);

    // The bounded rewind walk completes; the re-drive then rejects the cyclic definition with the
    // same clean depth-bound error rather than a StackOverflowError.
    assertThatThrownBy(() -> fixture.runtime().retry(runId, "s1", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("circular blueprint reference");
  }

  @Test
  void retry_unknown_step_is_rejected_as_not_found() {
    StepDefinition s1 = resourceStep("s1", "/examples/sample.txt", "k1");
    WorkflowDefinition workflow = workflow("wf-retry-unknown", Map.of(), List.of(s1));

    Fixture fixture = fixture(workflow);
    String runId = seedFailedRun(fixture, workflow);

    assertThatThrownBy(() -> fixture.runtime().retry(runId, "ghost", "user"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found in workflow 'wf-retry-unknown'");
  }

  private static long countEvents(Fixture fixture, String runId, String stepId,
      WorkflowEventType type) {
    List<WorkflowEvent> events = fixture.eventLog().getEvents(runId);
    return events.stream()
        .filter(event -> event.eventType() == type)
        .filter(event -> stepId == null ? event.stepId() == null : stepId.equals(event.stepId()))
        .count();
  }

  private String seedFailedRun(Fixture fixture, WorkflowDefinition workflow) {
    String runId = "failed-" + workflow.id();
    WorkflowState seeded = new WorkflowState(runId, workflow.id(), null,
        Instant.parse("2026-05-01T12:00:00Z"));
    seeded.setStatus(WorkflowStatus.FAILED);
    fixture.stateRepository().save(seeded);
    return runId;
  }

  private static StepDefinition resourceStep(String stepId, String resourcePath, String contextKey) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new ResourceBehaviour(resourcePath, contextKey, StepTransition.AUTO))
        .build();
  }

  private static StepDefinition agentStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new AgentBehaviour(stepId + "-agent", StepTransition.AUTO, null))
        .withContextMapping(ContextMapping.none())
        .build();
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

  private static StepDefinition failStep(String stepId) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new FailBehaviour("expected"))
        .build();
  }

  private static StepDefinition workflowStep(String stepId, String workflowRef) {
    return StepDefinition.builder()
        .withStepId(stepId)
        .withName(stepId)
        .withBehaviour(new WorkflowBehaviour(workflowRef, StepTransition.AUTO))
        .build();
  }

  private static BlueprintDefinition blueprint(String blueprintId, List<Executable> steps) {
    return new BlueprintDefinition(
        blueprintId,
        blueprintId,
        new BlueprintBehaviour(LoopConfig.withDefaults(
            LoopTerminationStrategy.FIXED_COUNT,
            null,
            null,
            1,
            null), StepTransition.AUTO),
        steps);
  }

  private static WorkflowDefinition workflow(String id,
      Map<String, BlueprintDefinition> blueprints,
      List<Executable> steps) {
    return new WorkflowDefinition(
        id,
        id,
        null,
        null,
        null,
        null,
        null,
        WorkflowSource.CUSTOM,
        WorkflowLifecycle.ACTIVE,
        Map.of(),
        blueprints,
        steps, List.of());
  }

  private static Fixture fixture(WorkflowDefinition workflow) {
    return fixture(workflow, defaultAgentInvoker());
  }

  private static Fixture fixture(WorkflowDefinition workflow, AgentInvoker agentInvoker) {
    return fixture(Map.of(workflow.id(), workflow), agentInvoker);
  }

  private static Fixture fixture(Map<String, WorkflowDefinition> workflows,
      AgentInvoker agentInvoker) {
    WorkflowStateRepository stateRepository = new InMemoryWorkflowStateRepository();
    WorkflowEventLog eventLog = new InMemoryWorkflowEventLog();
    Clock clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);

    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(workflows))
        .workflowStateRepository(stateRepository)
        .workflowEventLog(eventLog)
        .agentInvoker(agentInvoker)
        .clock(clock)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build();

    return new Fixture(runtime, stateRepository, eventLog);
  }

  private static AgentInvoker defaultAgentInvoker() {
    LlmClientResolver resolver = mock(LlmClientResolver.class);
    LlmClient client = mock(LlmClient.class);
    when(client.getProviderName()).thenReturn("openai");
    when(resolver.resolve(any())).thenReturn(client);
    when(resolver.listAvailableClients()).thenReturn(List.of("openai"));
    AgentRepository agentRepository = mock(AgentRepository.class);
    EventRecorder eventRecorder = new EventRecorder(new InMemoryWorkflowEventLog(),
        Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC));
    ObjectMapper mapper = new ObjectMapper();
    return AgentInvoker.builder()
        .agentRepository(agentRepository)
        .llmClientResolver(resolver)
        .contextRenderer(new ContextRenderer(mapper))
        .llmCommandParser(new LlmCommandParser(mapper))
        .objectMapper(mapper)
        .eventRecorder(eventRecorder)
        .llmProviderSelectionStrategy(new FirstAvailableProviderSelectionStrategy())
        .promptCacheEnabled(true)
        .llmCallObserver(new LlmCallObserver(eventRecorder, mapper))
        .modelTierResolver((provider, tier) -> null)
        .build();
  }

  private record Fixture(WorkflowRuntime runtime, WorkflowStateRepository stateRepository,
                         WorkflowEventLog eventLog) {

  }
}
