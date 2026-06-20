// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.transition;

import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.ApprovalOutcome;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.agentforge4j.verification.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Black-box coverage of the human step-transition gates: {@code HUMAN_REVIEW} parks at
 * {@code AWAITING_REVIEW} and resumes on a review note; {@code HUMAN_APPROVAL} parks at
 * {@code AWAITING_STEP_APPROVAL} and resumes on a step-approval decision.
 */
class TransitionGateTest {

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/transition/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/transition/agents"))
        .script(script())
        .build();
  }

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/transition/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read transition fake script", e);
    }
  }

  @Test
  void humanReviewParksAwaitingReview() {
    WorkflowRunResult result = harness().run("tr-review");
    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_REVIEW);
  }

  @Test
  void humanReviewResumesOnReviewNote() {
    WorkflowRunResult result = harness().run("tr-review",
        List.of(GateResponse.review("looks good")));
    WorkflowRunAssert.assertThat(result).isCompleted();
  }

  @Test
  void humanApprovalParksAwaitingStepApproval() {
    WorkflowRunResult result = harness().run("tr-approval");
    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_STEP_APPROVAL)
        .approvalRequested("run");
  }

  @Test
  void humanApprovalResumesOnApproval() {
    WorkflowRunResult result = harness().run("tr-approval",
        List.of(GateResponse.approveStep("approved")));
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .approvalDecision("run", ApprovalOutcome.APPROVED);
  }
}
