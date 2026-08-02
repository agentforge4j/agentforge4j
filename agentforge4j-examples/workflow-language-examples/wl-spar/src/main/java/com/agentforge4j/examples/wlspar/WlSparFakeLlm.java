// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlspar;

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
 * the single source of truth for the scripted responses: the offline run path in {@link WlSparApp} and the
 * test both obtain their fake provider here, so they exercise exactly the same exchange.
 *
 * <p>The script scripts each agent's per-round turn, keyed by call ordinal per agent: in round one both the
 * primary and the challenger emit a {@code CONTINUE} with a substantive reason, asking for another round;
 * in round two — the last round — both decline; then the primary's resolution turn (its third call) emits
 * {@code COMPLETE}. No real model, network, or API key is involved on the offline path.
 */
final class WlSparFakeLlm {

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private static final String DECLINE_ANOTHER_ROUND =
      "[{\"type\":\"CONTINUE\",\"wantsAnotherRound\":false}]";
  private static final String COMPLETE = "[{\"type\":\"COMPLETE\"}]";

  private WlSparFakeLlm() {
  }

  /**
   * Builds a resolver wrapping a single fake client scripted to run both rounds and then resolve.
   *
   * @return a resolver backed solely by the scripted fake client
   */
  static LlmClientResolver resolver() {
    FakeScript script = new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of(
        new FakeScriptKey(WlSparApp.WORKFLOW_ID, WlSparApp.REVIEW_STEP_ID, WlSparApp.PRIMARY_AGENT_ID, 0),
        new FakeResponse(continueRound("The retry policy for failed calls is unspecified."), null),
        new FakeScriptKey(WlSparApp.WORKFLOW_ID, WlSparApp.REVIEW_STEP_ID, WlSparApp.CHALLENGER_AGENT_ID, 0),
        new FakeResponse(continueRound("Input validation for the request payload is missing."), null),
        new FakeScriptKey(WlSparApp.WORKFLOW_ID, WlSparApp.REVIEW_STEP_ID, WlSparApp.PRIMARY_AGENT_ID, 1),
        new FakeResponse(DECLINE_ANOTHER_ROUND, null),
        new FakeScriptKey(WlSparApp.WORKFLOW_ID, WlSparApp.REVIEW_STEP_ID, WlSparApp.CHALLENGER_AGENT_ID, 1),
        new FakeResponse(DECLINE_ANOTHER_ROUND, null),
        new FakeScriptKey(WlSparApp.WORKFLOW_ID, WlSparApp.REVIEW_STEP_ID, WlSparApp.PRIMARY_AGENT_ID, 2),
        new FakeResponse(COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));
    return new DefaultLlmClientResolver(List.of(fakeLlmClient));
  }

  private static String continueRound(String reason) {
    return "[{\"type\":\"CONTINUE\",\"wantsAnotherRound\":true,\"reason\":\"%s\"}]".formatted(reason);
  }
}
