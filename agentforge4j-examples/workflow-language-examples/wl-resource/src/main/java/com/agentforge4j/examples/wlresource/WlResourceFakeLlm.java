// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlresource;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import java.util.List;
import java.util.Map;

/**
 * Owns this example's fake-LLM resolver. Unlike the other examples this workflow runs no agent, so the
 * script is intentionally empty — the runtime still requires an LLM resolver, and this satisfies that
 * requirement without ever being consulted. It is kept as a separate type for surface consistency with the
 * sibling examples, where this class is the single source of truth for the scripted responses.
 *
 * <p>This example has no meaningful real-provider execution mode because no agent step is ever invoked, so
 * no model is ever called. That is why it intentionally differs from the other five examples: there is no
 * {@code ExampleLlmConfig}, no real/fake selection, and no {@code example.properties} / {@code .env.example}
 * toggle — the offline run and the test both use this empty fake unconditionally.
 */
final class WlResourceFakeLlm {

  /**
   * Schema version the {@link FakeScript} is authored against. The fake provider requires a positive
   * version; there is a single script schema version today.
   */
  private static final int FAKE_SCRIPT_SCHEMA_VERSION = 1;

  private WlResourceFakeLlm() {
  }

  /**
   * Builds a resolver wrapping a single fake client with an empty script. No agent runs in this workflow,
   * so the script is never consulted; the resolver exists only to satisfy the runtime's requirement.
   *
   * @return a resolver backed solely by an empty-script fake client
   */
  static LlmClientResolver resolver() {
    LlmClient fakeLlmClient =
        new FakeLlmClient(new StaticFakeResponseSource(new FakeScript(FAKE_SCRIPT_SCHEMA_VERSION, Map.of())));
    return new DefaultLlmClientResolver(List.of(fakeLlmClient));
  }
}
