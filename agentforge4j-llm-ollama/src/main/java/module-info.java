// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.ollama.OllamaConfigurationAdapter;
import com.agentforge4j.llm.ollama.OllamaLlmClientFactory;

/**
 * Ollama local-server adapter implementing {@link LlmClientFactory} for on-machine models.
 *
 * <p>Targets the Ollama HTTP API; suitable for offline or air-gapped setups where cloud providers
 * are unavailable. Same abstraction boundary as other {@code agentforge4j.llm.*} modules.
 */
module agentforge4j.llm.ollama {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires org.apache.commons.lang3;
  requires java.net.http;
  requires static lombok;
  opens com.agentforge4j.llm.ollama.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
    with OllamaLlmClientFactory;
  provides LlmClientConfigurationAdapter
    with OllamaConfigurationAdapter;
}
