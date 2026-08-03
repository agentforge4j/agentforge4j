// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientFactory;

/**
 * Provider-neutral LLM client contracts and {@link LlmClientFactory} service interface.
 *
 * <p>Normalizes request/response shapes for callers that use the shared LLM API while vendor specifics
 * stay in separate {@code agentforge4j.llm.*} adapter modules. ServiceLoader-style {@code uses} and
 * {@code provides} declarations are the supported extension point; the embedding application selects
 * factories. Model text does not substitute for engine-owned workflow control where that applies.
 */
module agentforge4j.llm {
  requires transitive agentforge4j.llm.api;
  // transitive: LlmClientFactoryContext (exported) takes ObjectMapper directly as a parameter.
  requires transitive com.fasterxml.jackson.databind;
  requires org.apache.commons.lang3;
  requires java.net.http;
  requires static lombok;
  requires agentforge4j.util;
  exports com.agentforge4j.llm;
  uses LlmClientFactory;
}
