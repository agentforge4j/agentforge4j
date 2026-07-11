// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlbranch;

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
 * the single source of truth for the scripted responses: the offline run path in {@link WlBranchApp} and
 * the test both obtain their fake provider here, so they exercise exactly the same responses.
 *
 * <p>The script feeds the {@code decide} agent a {@code SET_CONTEXT} command carrying the requested
 * decision, and the approved branch's agent a {@code COMPLETE}, so each branch can be driven from one
 * workflow definition without a real model, network, or API key.
 */
final class WlBranchFakeLlm {

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private WlBranchFakeLlm() {
  }

  /**
   * Builds a resolver wrapping a single fake client scripted to emit the given branch decision.
   *
   * @param decision the decision the {@code decide} agent writes into the context
   *                 (for example {@link WlBranchApp#APPROVE} or {@link WlBranchApp#REJECT})
   *
   * @return a resolver backed solely by the scripted fake client
   */
  static LlmClientResolver resolver(String decision) {
    return new DefaultLlmClientResolver(List.of(client(decision)));
  }

  private static LlmClient client(String decision) {
    String setDecision =
        "[{\"type\":\"SET_CONTEXT\",\"key\":\"%s\",\"value\":{\"type\":\"STRING\",\"value\":\"%s\"}}]"
            .formatted(WlBranchApp.DECISION_KEY, decision);
    FakeScript script = new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of(
        new FakeScriptKey(WlBranchApp.WORKFLOW_ID, WlBranchApp.DECIDE_STEP_ID,
            WlBranchApp.BRANCH_AGENT_ID, 0),
        new FakeResponse(setDecision, null),
        new FakeScriptKey(WlBranchApp.WORKFLOW_ID, WlBranchApp.APPROVE_STEP_ID,
            WlBranchApp.APPROVE_AGENT_ID, 0),
        new FakeResponse("[{\"type\":\"COMPLETE\"}]", null)));
    return new FakeLlmClient(new StaticFakeResponseSource(script));
  }
}
