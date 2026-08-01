// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.retry;

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
 * End-to-end proof that a {@code RETRY_PREVIOUS} step whose {@code FROM_STEP} replay range contains
 * a composite (a completed signal-loop blueprint) is rejected fail-closed, rather than silently
 * skipping the composite's cleared-but-unreplayed state.
 *
 * <p>The workflow is {@code anchor} (INPUT) → {@code loop-bp} (AGENT_SIGNAL loop) → {@code retry}
 * (top-level {@code RETRY_PREVIOUS} rewinding to {@code anchor}, {@code FROM_STEP}). Once
 * {@code loop-bp} has completed and {@code retry} dispatches, the loop blueprint sits inside the
 * checked replay range — {@code retry}'s own inline replay never re-executes it, so its state would
 * be silently stale for anything running after {@code retry} in the same drive. There is no safe
 * "trailing composite" exception for this shape, so it is rejected before any state mutation, and
 * the run fails rather than completing.
 */
class RetryPreviousLoopTest {

  private static FakeScript script(String resource) {
    try {
      return new FakeScriptParser().parse(Files.readString(Fixtures.dir(resource)));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read retry fake script: " + resource, e);
    }
  }

  @Test
  void retryPreviousRewindThroughACompletedSignalLoopIsRejectedFailClosed() {
    WorkflowRunResult result = WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/retry/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/retry/agents"))
        .script(script("/fixtures/retry/script.json"))
        .build()
        // The single INPUT drives the loop to completion; the RETRY_PREVIOUS step then dispatches
        // and is rejected before any further pause, so only one gate response is ever consumed.
        .run("retry-loop", List.of(GateResponse.input(Map.of("go", "first"))));

    WorkflowRunAssert.assertThat(result)
        .isFailed()
        .stepVisitCount("loop-body", 1)
        .failedBecause("non-step executable");
  }

  /**
   * Same rejection proof, but the completed loop's body is a nested blueprint ref ({@code
   * outer-loop-bp} → {@code inner-bp} → {@code loop-body}) — the composite is still visible to the
   * replay-range check via the full executable list regardless of nesting depth.
   */
  @Test
  void retryPreviousRewindThroughACompletedNestedSignalLoopIsRejectedFailClosed() {
    WorkflowRunResult result = WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/retry/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/retry/agents"))
        .script(script("/fixtures/retry/script-nested.json"))
        .build()
        .run("retry-nested-loop", List.of(GateResponse.input(Map.of("go", "first"))));

    WorkflowRunAssert.assertThat(result)
        .isFailed()
        .stepVisitCount("loop-body", 1)
        .failedBecause("non-step executable");
  }
}
