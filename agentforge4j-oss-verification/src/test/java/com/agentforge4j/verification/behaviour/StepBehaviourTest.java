// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.behaviour;

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
 * Black-box coverage of the step behaviours (FAIL, BRANCH, RESOURCE, WORKFLOW nesting, INPUT, SPAR,
 * ASSIGN_CONTEXT). AGENT is exercised
 * throughout the command/loop tiers. Each behaviour drives a focused fixture and asserts its observable effect.
 */
class StepBehaviourTest {

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/behaviour/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/behaviour/agents"))
        .script(script())
        .build();
  }

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/behaviour/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read behaviour fake script", e);
    }
  }

  @Test
  void failBehaviourFailsTheRun() {
    WorkflowRunResult result = harness().run("beh-fail");
    WorkflowRunAssert.assertThat(result)
        .isFailed()
        // The throwing step records STEP_FAILED, then the run terminates with RUN_FAILED.
        .eventsInOrder(WorkflowEventType.STEP_FAILED, WorkflowEventType.RUN_FAILED);
  }

  @Test
  void branchRoutesToMatchedBranch() {
    WorkflowRunResult result = harness().run("beh-branch-match");
    WorkflowRunAssert.assertThat(result)
        .contextEquals("decision", "go")
        .isFailed();
  }

  @Test
  void branchFallsThroughToCompletionWhenUnmatchedAndNoDefault() {
    WorkflowRunResult result = harness().run("beh-branch-default");
    WorkflowRunAssert.assertThat(result)
        .contextEquals("decision", "other")
        .isCompleted();
  }

  @Test
  void resourceBehaviourLoadsContentIntoContext() {
    WorkflowRunResult result = harness().run("beh-resource");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        // Pin the actual fixture content (modulo trailing whitespace), not mere non-emptiness — a
        // resolver regression serving the wrong resource must not pass.
        .contextMatchesRegex("res.out", "resource-behaviour-content\\s*");
  }

  @Test
  void resourceBehaviourFailsClosedWhenTheResourceIsMissing() {
    WorkflowRunResult result = harness().run("beh-resource-missing");
    WorkflowRunAssert.assertThat(result)
        .isFailed()
        .failedBecause("does-not-exist")
        .eventsInOrder(WorkflowEventType.STEP_FAILED, WorkflowEventType.RUN_FAILED);
  }

  @Test
  void assignContextBehaviourWritesTheScalarValueEndToEnd() {
    WorkflowRunResult result = harness().run("beh-assign");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .contextEquals("assigned.tier", "POWERFUL");
  }

  @Test
  void nestedWorkflowCompletes() {
    WorkflowRunResult result = harness().run("beh-nested-outer");
    WorkflowRunAssert.assertThat(result).isCompleted();
  }

  @Test
  void sparAppliesResolutionRound() {
    WorkflowRunResult result = harness().run("beh-spar");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .contextHas("spar.primary.round.1");
  }

  @Test
  void sparWithHumanApprovalParksAwaitingStepApproval() {
    WorkflowRunResult result = harness().run("beh-spar-gated");
    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_STEP_APPROVAL)
        .approvalRequested("review");
  }

  @Test
  void sparWithHumanApprovalResumesToCompletionOnApproval() {
    WorkflowRunResult result = harness().run("beh-spar-gated",
        List.of(GateResponse.approveStep("looks good")));
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .contextHas("spar.primary.round.1");
  }

  @Test
  void inputBehaviourPausesAwaitingInput() {
    WorkflowRunResult result = harness().run("beh-input");
    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_INPUT)
        .inputRequested("collect");
  }

  @Test
  void inputBehaviourResumesOnSubmittedInput() {
    WorkflowRunResult result = harness().run("beh-input",
        List.of(GateResponse.input(Map.of("name", "Alice"))));
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .contextEquals("user-form.name", "Alice")
        // The run parks awaiting input and, once supplied, drives through to completion. The event
        // model carries no distinct input-supplied event, so the resume is observed as the
        // transition from AWAITING_INPUT to RUN_COMPLETED.
        .eventsInOrder(WorkflowEventType.AWAITING_INPUT, WorkflowEventType.RUN_COMPLETED);
  }
}
