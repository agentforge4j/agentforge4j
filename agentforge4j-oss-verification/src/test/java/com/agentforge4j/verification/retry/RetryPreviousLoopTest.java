// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.retry;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
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
 * End-to-end {@code RETRY_PREVIOUS} verification that a completed signal-loop's completion marker is
 * cleared by a rewind, so the loop re-executes instead of being permanently skipped.
 *
 * <p>The workflow is {@code anchor} (INPUT) → {@code loop-bp} (AGENT_SIGNAL loop) → {@code retry}
 * (top-level RETRY_PREVIOUS rewinding to {@code anchor}, FROM_STEP, one attempt). A
 * {@code RETRY_PREVIOUS} re-executes only the top-level steps in its rewound range — a blueprint
 * loop is not re-run inline — so rewinding to the pausing INPUT re-suspends the run; the resume
 * re-drive then re-reaches the loop, which re-runs only because the rewind cleared its completion
 * marker (the staleness fixed in {@code WorkflowState.clearEntriesFromUid}). On the second pass the
 * single retry attempt is exhausted and the run falls through to {@code done} and completes.
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
  void completedSignalLoopReRunsAfterRetryPreviousRewindClearsItsMarker() {
    WorkflowRunResult result = WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/retry/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/retry/agents"))
        .script(script("/fixtures/retry/script.json"))
        .build()
        // First INPUT drives the loop to completion then the retry rewinds and re-suspends the
        // INPUT; the second INPUT resumes into the re-drive that re-runs the now-unmarked loop.
        .run("retry-loop", List.of(
            GateResponse.input(Map.of("go", "first")),
            GateResponse.input(Map.of("go", "second"))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .stepVisitCount("loop-body", 2)
        // The RETRY_PREVIOUS step is entered on both passes — it rewinds on the first and its single
        // attempt is exhausted on the second — emitting STEP_RETRIED twice.
        .emittedEvent(WorkflowEventType.STEP_RETRIED)
        .eventCount(WorkflowEventType.STEP_RETRIED, 2);
  }

  /**
   * Same rewind proof, but the completed loop's body is a nested blueprint ref ({@code outer-loop-bp}
   * → {@code inner-bp} → {@code loop-body}). The completion marker must be keyed on the nested body
   * step's uid, not {@code 0}; otherwise the rewind never clears it and the loop is permanently
   * skipped, leaving {@code loop-body} visited only once. Regression for the nested-body case of
   * {@code BlueprintExecutor.loopBodyCompletionUid}.
   */
  @Test
  void completedNestedSignalLoopReRunsAfterRetryPreviousRewindClearsItsMarker() {
    WorkflowRunResult result = WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/retry/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/retry/agents"))
        .script(script("/fixtures/retry/script-nested.json"))
        .build()
        .run("retry-nested-loop", List.of(
            GateResponse.input(Map.of("go", "first")),
            GateResponse.input(Map.of("go", "second"))));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .stepVisitCount("loop-body", 2)
        // The RETRY_PREVIOUS step is entered on both passes — it rewinds on the first and its single
        // attempt is exhausted on the second — emitting STEP_RETRIED twice.
        .emittedEvent(WorkflowEventType.STEP_RETRIED)
        .eventCount(WorkflowEventType.STEP_RETRIED, 2);
  }
}
