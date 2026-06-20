// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.consumer;

import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.CapturingFileSink;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;

/**
 * Exercises representative public testkit signatures whose parameter/return types come from modules
 * the testkit only re-exports transitively: {@link FakeScript} ({@code agentforge4j.llm.fake}),
 * {@link ModelTier} ({@code agentforge4j.llm.api}), and {@link FileSink} ({@code agentforge4j.runtime},
 * the supertype of the testkit's {@link CapturingFileSink}). This class compiling against a module
 * that {@code requires} only {@code agentforge4j.testkit} is the JPMS contract proof.
 */
public final class TestkitModuleConsumer {

  /**
   * Loads a {@link FakeScript} through the testkit loader — naming a {@code agentforge4j.llm.fake}
   * type returned by the testkit API.
   *
   * @param scriptJson the fake-script JSON; must be a valid script
   *
   * @return the parsed script
   */
  public FakeScript loadScript(String scriptJson) {
    return new ScenarioScriptLoader().fromJson(scriptJson);
  }

  /**
   * Creates a testkit {@link CapturingFileSink} and returns it as the {@code agentforge4j.runtime}
   * {@link FileSink} supertype it implements.
   *
   * @return a new capturing file sink
   */
  public FileSink newFileSink() {
    return new CapturingFileSink();
  }

  /**
   * Calls {@link WorkflowRunAssert#providerCallTier(ModelTier)} — naming the
   * {@code agentforge4j.llm.api} {@link ModelTier} type in a testkit signature.
   *
   * @param result the run result; must not be {@code null}
   * @param tier   the expected provider-call tier; must not be {@code null}
   *
   * @return the assertion, for chaining
   */
  public WorkflowRunAssert assertProviderTier(WorkflowRunResult result, ModelTier tier) {
    return WorkflowRunAssert.assertThat(result).providerCallTier(tier);
  }
}
