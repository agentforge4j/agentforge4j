// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlloop;

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
 * the single source of truth for the scripted responses: the offline run path in {@link WlLoopApp} and
 * the test both obtain their fake provider here, so they exercise exactly the same responses.
 *
 * <p>The script supplies one response per loop iteration, keyed by call ordinal: the fixed-count loop
 * runs {@link WlLoopApp#FIXED_ITERATIONS} times (each body call emits {@code COMPLETE}, which
 * {@code FIXED_COUNT} ignores); the agent-signal loop emits {@code COMPLETE} on its first iteration, which
 * signals it to stop. No real model, network, or API key is involved on the offline path.
 */
final class WlLoopFakeLlm {

  private static final String COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private WlLoopFakeLlm() {
  }

  /**
   * Builds a resolver wrapping a single fake client scripted with one response per loop iteration.
   *
   * @return a resolver backed solely by the scripted fake client
   */
  static LlmClientResolver resolver() {
    FakeScript script = new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of(
        new FakeScriptKey(WlLoopApp.FIXED_WORKFLOW_ID, WlLoopApp.BODY_STEP_ID, WlLoopApp.LOOP_AGENT_ID, 0),
        new FakeResponse(COMPLETE, null),
        new FakeScriptKey(WlLoopApp.FIXED_WORKFLOW_ID, WlLoopApp.BODY_STEP_ID, WlLoopApp.LOOP_AGENT_ID, 1),
        new FakeResponse(COMPLETE, null),
        new FakeScriptKey(WlLoopApp.FIXED_WORKFLOW_ID, WlLoopApp.BODY_STEP_ID, WlLoopApp.LOOP_AGENT_ID, 2),
        new FakeResponse(COMPLETE, null),
        new FakeScriptKey(WlLoopApp.SIGNAL_WORKFLOW_ID, WlLoopApp.BODY_STEP_ID, WlLoopApp.LOOP_AGENT_ID, 0),
        new FakeResponse(COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));
    return new DefaultLlmClientResolver(List.of(fakeLlmClient));
  }
}
