// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.gemini.GeminiConfigurationAdapter;
import com.agentforge4j.llm.gemini.GeminiLlmClientFactory;

/**
 * Google Gemini generative-language adapter registered as an {@link LlmClientFactory} implementation.
 *
 * <p>Exports Gemini-specific types where callers need direct access; otherwise behaves like other
 * provider jars by supplying a factory for the shared {@code agentforge4j.llm} abstractions.
 */
module agentforge4j.llm.gemini {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires static lombok;
  requires org.apache.commons.lang3;
  requires java.net.http;
  exports com.agentforge4j.llm.gemini;
  opens com.agentforge4j.llm.gemini.dto to com.fasterxml.jackson.databind;
  uses LlmClientFactory;
  provides LlmClientFactory
    with GeminiLlmClientFactory;
  provides LlmClientConfigurationAdapter
    with GeminiConfigurationAdapter;
}
