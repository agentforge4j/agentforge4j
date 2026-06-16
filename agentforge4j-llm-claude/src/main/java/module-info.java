// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.claude.ClaudeLlmClientFactory;

/**
 * Anthropic Claude Messages API adapter exposed as an {@link LlmClientFactory} provider.
 *
 * <p>Encapsulates Claude-specific request/response DTOs and HTTP details while adapting them to the
 * shared {@code agentforge4j.llm} client abstraction for applications that assemble this provider on the module path.
 */
module agentforge4j.llm.claude {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires static lombok;
  requires org.apache.commons.lang3;
  opens com.agentforge4j.llm.claude.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
      with ClaudeLlmClientFactory;
}
