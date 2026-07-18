// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.fake.FakeLlmClientFactory;

/**
 * Deterministic scripted LLM provider: replays pre-registered responses instead of calling a model.
 *
 * <p>Discovered via {@link java.util.ServiceLoader} like any other {@code agentforge4j.llm.*}
 * adapter. Unlike HTTP providers it has no transport or retry — responses come from a {@code FakeResponseSource} keyed
 * by the request's invocation identity and a per-run ordinal. Suitable for local development, demos, sandbox mode, and
 * workflow verification.
 */
module agentforge4j.llm.fake {
  // transitive: the exported package's FakeLlmClientFactory/FakeConfiguration surface exposes
  // LlmClient/LlmClientConfiguration/LlmInvocationException (agentforge4j.llm) directly to callers.
  requires transitive agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires com.networknt.schema;

  exports com.agentforge4j.llm.fake;

  provides LlmClientFactory with FakeLlmClientFactory;
}
