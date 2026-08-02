// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.branch;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.verification.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage: a gated step reachable only as a {@code BRANCH} target was absent
 * from {@code ReachableStepGraph} (the branch step itself was recorded, but
 * {@code BranchBehaviour.childExecutables()} was never descended), so
 * {@code DefaultWorkflowRuntime.gateCompletedStep} could not resolve it and silently skipped its
 * {@code HUMAN_REVIEW}/{@code HUMAN_APPROVAL} transition on resume — a human-gate bypass.
 *
 * <p>The workflow routes to an {@code INPUT} step ({@code gated-input}) as a branch target. That
 * step's own suspend (waiting for the answer) works regardless of the bug, since a branch target is
 * dispatched directly by {@code BranchBehaviourHandler}. The bug surfaces one step later: submitting
 * the answer drives {@code DefaultWorkflowRuntime.submitInput}, which must resolve
 * {@code gated-input} by id to honour its {@code HUMAN_REVIEW} transition before the run is allowed
 * to continue — pre-fix, that resolution failed, and the run rejoined {@code drive()} and completed
 * without ever suspending for review.
 */
class BranchGateResolutionTest {

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/branch-gate/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/branch-gate/agents"))
        .script(script())
        .build();
  }

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/branch-gate/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read branch-gate fake script", e);
    }
  }

  @Test
  void submittingAnswerToGatedInputUnderBranchSuspendsForReview() {
    WorkflowRunResult result = harness().run("gate-under-branch",
        List.of(GateResponse.input(Map.of("name", "Alice"))));

    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_REVIEW)
        .emittedEvent(WorkflowEventType.STEP_AWAITING_REVIEW)
        .didNotEmitEvent(WorkflowEventType.RUN_COMPLETED);
  }

  /**
   * Same regression, routed through {@code childExecutables()}'s <b>default-branch</b> arm: the
   * resolved decision ({@code "go"}) matches no exact-match key ({@code "stop"} is the only one),
   * so the gated {@code INPUT} step is reached only via {@code BranchBehaviour.defaultBranch()}.
   */
  @Test
  void submittingAnswerToGatedInputUnderBranchDefaultSuspendsForReview() {
    WorkflowRunResult result = harness().run("gate-under-branch-default",
        List.of(GateResponse.input(Map.of("name", "Alice"))));

    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_REVIEW)
        .emittedEvent(WorkflowEventType.STEP_AWAITING_REVIEW)
        .didNotEmitEvent(WorkflowEventType.RUN_COMPLETED);
  }

  /**
   * Same regression, routed through {@code childExecutables()}'s <b>predicate</b> arm: the gated
   * {@code INPUT} step is reached only via a matched {@code MEMBER_OF} predicate's target, not an
   * exact-match branch or the default.
   */
  @Test
  void submittingAnswerToGatedInputUnderBranchPredicateSuspendsForReview() {
    WorkflowRunResult result = harness().run("gate-under-branch-predicate",
        List.of(GateResponse.input(Map.of("name", "Alice"))));

    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_REVIEW)
        .emittedEvent(WorkflowEventType.STEP_AWAITING_REVIEW)
        .didNotEmitEvent(WorkflowEventType.RUN_COMPLETED);
  }
}
