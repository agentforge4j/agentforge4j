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
  void fixedCountLoopInvokesBodyOncePerIteration() {
    // Regression for CR-1: the body step must actually execute on every iteration, not just the
    // first (shouldSkip previously treated the step's iteration-1 output as "already done" for
    // iterations 2 and 3, so LOOP_ITERATION_STARTED/COMPLETED fired 3 times with the body silently
    // skipped on 2 of them).
    WorkflowRunResult result = harness().run("fixed-count-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .loopIterations(3)
        .stepVisitCount("body", 3)
        .providerCallCount(3);
  }

  @Test
  void forEachLoopRunsOncePerListElement() {
    WorkflowRunResult result = harness().run("foreach-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .forEachIterations(2);
  }

  @Test
  void forEachLoopInvokesBodyForEveryElementNotJustTheFirst() {
    // Regression for CR-1's most severe consequence: FOR_EACH silently processed only the first
    // list element (elements 2..N were skipped, not just under-invoked) while still emitting
    // LOOP_ITERATION_STARTED/COMPLETED for every element, so the audit trail claimed all elements
    // were processed. The seed step provisions a 2-element list ("a", "b"); the body must be
    // visited (and the agent invoked) once per element.
    WorkflowRunResult result = harness().run("foreach-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .forEachIterations(2)
        .stepVisitCount("body", 2)
        .providerCallCount(3); // 1 seed call + 1 body call per element
  }

  @Test
  void evaluatorLoopRunsUntilEvaluatorSignalsComplete() {
    WorkflowRunResult result = harness().run("evaluator-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .loopIterations(2);
  }

  @Test
  void evaluatorLoopInvokesBodyOnBothIterations() {
    // Regression for CR-1: the evaluator agent itself is invoked outside StepSequenceExecutor
    // (unaffected by the bug), but the loop body's own "body" step was silently skipped on
    // iteration 2 because its iteration-1 output already sat in stepOutputs.
    WorkflowRunResult result = harness().run("evaluator-loop");
    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .loopIterations(2)
        .stepVisitCount("body", 2);
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
