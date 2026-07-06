// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.retry;

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
 * Regression coverage for CR-2: a {@code RETRY_PREVIOUS} step whose {@code retryStepId} targets an
 * {@code AGENT} (or {@code SPAR}) step crashed the run. {@code RetryPreviousBehaviourHandler}
 * cleared the target's execution uid (via {@code clearEntriesFromUid}) then dispatched it directly
 * through {@code ExecutableExecutor}, bypassing the {@code StepSequenceExecutor} allocation point
 * that normally assigns a step its execution uid — so {@code AgentBehaviourHandler} failed on a
 * {@code null} current-step uid.
 */
class RetryPreviousAgentTargetTest {

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/retry/script-agent-retry.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read retry fake script", e);
    }
  }

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/retry/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/retry/agents"))
        .script(script())
        .build();
  }

  @Test
  void retryPreviousSingleStepToAgentTargetCompletesInsteadOfFailing() {
    WorkflowRunResult result = harness().run("retry-previous-agent-single");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .stepVisitCount("seed", 2)
        .didNotVisitStep("fallback");
  }

  @Test
  void retryPreviousFromStepToAgentRangeCompletesInsteadOfFailing() {
    WorkflowRunResult result = harness().run("retry-previous-agent-from-step");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .stepVisitCount("seedA", 2)
        .stepVisitCount("seedB", 2)
        .didNotVisitStep("fallback");
  }
}
