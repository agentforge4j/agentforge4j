// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.negative;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.CapturedFile;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.verification.support.Fixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tier 8 — negative / abuse / edge (security). Each case drives untrusted model output through the
 * real runtime chain (parser → command handlers → file sink) and asserts the run fails closed, or the
 * production guard rejects the abuse, pinning the specific failure reason. The agent
 * ({@code neg-agent}) opts into no command restriction, so {@code CREATE_FILE}/{@code COMPLETE} are
 * allowed; the deterministic fake supplies the raw output.
 *
 * <p>A parse failure is retried by the agent invoker ({@code RETRY_ATTEMPTS = 2}); the malformed-output
 * cases therefore script <em>both</em> the first call and its retry with the same bad output, so the
 * run fails on the parse rejection itself rather than on an unscripted retry miss.
 */
class NegativeSecurityTest {

  private WorkflowTestHarness.Builder harness(FakeScript script) {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/negative/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/negative/agents"))
        .script(script);
  }

  /** Scripts both the initial call (ordinal 0) and its retry (ordinal 1) with the same output. */
  private static FakeScript bothAttempts(String agentOutput) {
    return new FakeScript(1, Map.of(
        new FakeScriptKey("negative-run", "run", "neg-agent", 0), new FakeResponse(agentOutput, null),
        new FakeScriptKey("negative-run", "run", "neg-agent", 1), new FakeResponse(agentOutput, null)));
  }

  private static FakeScript once(String agentOutput) {
    return new FakeScript(1, Map.of(
        new FakeScriptKey("negative-run", "run", "neg-agent", 0), new FakeResponse(agentOutput, null)));
  }

  @Test
  void malformedJsonOutputFailsTheRun() {
    WorkflowRunResult result = harness(bothAttempts("this is not valid json")).build().run("negative-run");
    WorkflowRunAssert.assertThat(result)
        .isFailed()
        .failedBecause("not valid JSON")
        // The rejection surfaces as a step-level failure (STEP_FAILED) that fails the run
        // (RUN_FAILED) — the contract is exercised through the harness, not just the final state.
        .eventsInOrder(WorkflowEventType.STEP_FAILED, WorkflowEventType.RUN_FAILED);
  }

  @Test
  void unknownCommandTypeFailsTheRun() {
    WorkflowRunResult result =
        harness(bothAttempts("[{\"type\":\"BOGUS\"}]")).build().run("negative-run");
    WorkflowRunAssert.assertThat(result).isFailed().failedBecause("Unknown command type 'BOGUS'");
  }

  @Test
  void missingRequiredCommandFieldFailsTheRun() {
    WorkflowRunResult result =
        harness(bothAttempts("[{\"type\":\"CREATE_FILE\"}]")).build().run("negative-run");
    WorkflowRunAssert.assertThat(result).isFailed().failedBecause("missing required field");
  }

  @Test
  void missingScriptedResponseFailsClosed() {
    // No entry for the agent's invocation key — the fake never fabricates a default.
    WorkflowRunResult result = harness(new FakeScript(1, Map.of())).build().run("negative-run");
    WorkflowRunAssert.assertThat(result)
        .isFailed()
        .failedBecause("No fake response for key")
        // The LLM-call exception (LlmInvocationException, non-transient) surfaces as a step-level
        // failure that fails the run — LLM exception → STEP_FAILED → RUN_FAILED via the existing
        // event model (no LLM-failure event type exists, and none is added).
        .eventsInOrder(WorkflowEventType.STEP_FAILED, WorkflowEventType.RUN_FAILED);
  }

  @Test
  void pathTraversalCreateFileIsRejectedAndNothingEscapes(@TempDir Path sinkDir) throws Exception {
    // baseDir/runId/../../escape.txt resolves above baseDir → LocalFileSink rejects via requireWithinBase.
    WorkflowRunResult result =
        harness(once("[{\"type\":\"CREATE_FILE\",\"path\":\"../../escape.txt\",\"content\":\"x\"}]"))
            .fileSinkDir(sinkDir)
            .build()
            .run("negative-run");

    WorkflowRunAssert.assertThat(result).isFailed().failedBecause("Path escapes base directory");
    assertThat(Files.exists(sinkDir.getParent().resolve("escape.txt"))).isFalse();
    try (var tree = Files.walk(sinkDir)) {
      assertThat(tree.noneMatch(p -> p.getFileName().toString().equals("escape.txt"))).isTrue();
    }
  }

  @Test
  void oversizedResponseContentPassesThroughWithoutTruncation() {
    // No response-size limit is enforced anywhere in the parse → handle path (recorded gap, mirrored
    // by the runtime's own LlmCommandParserSecurityTest). This black-box guard pins that a large
    // payload survives the full chain byte-for-byte — no OOM, no silent truncation.
    String body = "A".repeat(200_000);
    String output = "[{\"type\":\"CREATE_FILE\",\"path\":\"out/big.txt\",\"content\":\""
        + body + "\"},{\"type\":\"COMPLETE\"}]";
    WorkflowRunResult result = harness(once(output)).build().run("negative-run");

    WorkflowRunAssert.assertThat(result).isCompleted().createdFile("out/big.txt");
    assertThat(result.captures().files())
        .singleElement()
        .extracting(CapturedFile::content)
        .asString()
        .hasSize(200_000);
  }
}
