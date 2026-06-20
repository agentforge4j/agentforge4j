// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.loop;

import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.verification.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Black-box coverage of the four loop-termination strategies, including AGENT_SIGNAL: an agent body
 * that emits {@code COMPLETE} now terminates the loop by signal (agentforge4j/agentforge4j#97 fixed —
 * {@code COMPLETE} surfaces as {@code COMPLETED_SIGNAL} to {@code AgentSignalLoopStrategy}). Each loop
 * is a {@code BLUEPRINT_REF} step into a loop blueprint; iteration count is asserted via
 * {@code loopIterations}/{@code forEachIterations} (backed by {@code LOOP_ITERATION_STARTED} events).
 */
class LoopStrategyTest {

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/loop/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/loop/agents"))
        .script(script())
        .build();
  }

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(Files.readString(Fixtures.dir("/fixtures/loop/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read loop fake script", e);
    }
  }

  @Test
  void fixedCountLoopRunsExactlyMaxIterations() {
    WorkflowRunResult result = harness().run("fixed-count-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .loopIterations(3);
  }

  @Test
  void forEachLoopRunsOncePerListElement() {
    WorkflowRunResult result = harness().run("foreach-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .forEachIterations(2);
  }

  @Test
  void evaluatorLoopRunsUntilEvaluatorSignalsComplete() {
    WorkflowRunResult result = harness().run("evaluator-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .loopIterations(2);
  }

  @Test
  void agentSignalLoopTerminatesWhenAgentEmitsComplete() {
    // The body signals completion (COMPLETE) on its first iteration, so the loop terminates by
    // signal and the run completes. Before #97 the COMPLETE was ignored and the loop ran to
    // maxIterations (5, FAIL) — this asserts the corrected single-signal termination.
    WorkflowRunResult result = harness().run("agent-signal-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .loopIterations(1);
  }
}
