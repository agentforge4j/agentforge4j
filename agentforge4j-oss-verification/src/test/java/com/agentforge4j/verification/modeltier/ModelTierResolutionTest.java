// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.modeltier;

import com.agentforge4j.llm.api.ModelTier;
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
 * Verifies model-tier resolution end to end: an agent whose provider preference carries no model pin
 * resolves a concrete model from its declared {@link ModelTier} (shipped defaults), and a step-level
 * tier overrides the agent tier. The resolved tier is observed via the {@code LLM_CALL_COMPLETED}
 * audit payload behind {@link WorkflowRunAssert#providerCallTier(ModelTier)}.
 */
class ModelTierResolutionTest {

  private WorkflowTestHarness harness() {
    return WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/modeltier/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/modeltier/agents"))
        .script(script())
        .build();
  }

  private static FakeScript script() {
    try {
      String json = Files.readString(Fixtures.dir("/fixtures/modeltier/script.json"));
      return new FakeScriptParser().parse(json);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read model-tier fake script", e);
    }
  }

  @Test
  void agentTierResolvesConcreteModelForProviderCall() {
    WorkflowRunResult result = harness().run("agent-tier");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .providerCallCount(1)
        .providerCallTier(ModelTier.STANDARD);
  }

  @Test
  void stepTierOverridesAgentTier() {
    WorkflowRunResult result = harness().run("step-tier");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .providerCallCount(1)
        .providerCallTier(ModelTier.POWERFUL);
  }

  @Test
  void premiumStepTierLoadsFromConfigAndResolves() {
    // Proves the fourth tier survives the file-config path end to end — declared as a step
    // override in workflow JSON, parsed by the loader, resolved into the provider call. It does
    // not prove schema acceptance: no production loader validates bundles against the JSON
    // schema (see WorkflowSchemaCollectionStepRejectionTest), so this run would pass even with
    // PREMIUM missing from the enum. ModelTierSchemaAlignmentTest is the guard for that half.
    WorkflowRunResult result = harness().run("premium-step-tier");

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .providerCallCount(1)
        .providerCallTier(ModelTier.PREMIUM);
  }
}
