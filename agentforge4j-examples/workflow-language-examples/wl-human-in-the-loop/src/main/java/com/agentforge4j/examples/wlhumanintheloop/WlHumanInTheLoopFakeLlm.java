// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlhumanintheloop;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import java.util.List;
import java.util.Map;

/**
 * Owns this example's deterministic fake-LLM script and exposes it as an {@link LlmClientResolver}. It is
 * the single source of truth for the scripted responses: the offline run path in
 * {@link WlHumanInTheLoopApp} and the test both obtain their fake provider here, so they exercise exactly
 * the same responses.
 *
 * <p>The script feeds the reviewer agent's single call a lone {@code COMPLETE}, after which the approval
 * gate suspends the run. The input and the approval decision are supplied directly in code (they are human
 * gates, not model output), so no real model, network, or API key is involved on the offline path.
 */
final class WlHumanInTheLoopFakeLlm {

  /**
   * The scripted model output for the reviewer agent's single call: a lone {@code COMPLETE} finishes
   * the step, after which the approval gate suspends the run.
   */
  private static final String SCRIPTED_COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private WlHumanInTheLoopFakeLlm() {
  }

  /**
   * Builds a resolver wrapping a single fake client scripted to complete the reviewer agent's step.
   *
   * @return a resolver backed solely by the scripted fake client
   */
  static LlmClientResolver resolver() {
    FakeScript script = new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of(
        new FakeScriptKey(WlHumanInTheLoopApp.WORKFLOW_ID, WlHumanInTheLoopApp.REVIEW_STEP_ID,
            WlHumanInTheLoopApp.REVIEW_AGENT_ID, 0),
        new FakeResponse(SCRIPTED_COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));
    return new DefaultLlmClientResolver(List.of(fakeLlmClient));
  }
}
