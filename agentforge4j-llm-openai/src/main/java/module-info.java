// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.openai.OpenAiConfigurationAdapter;
import com.agentforge4j.llm.openai.OpenAiLlmClientFactory;

/**
 * OpenAI HTTP adapter implementing {@link LlmClientFactory} for chat/completions-style APIs.
 *
 * <p>Maps OpenAI JSON to the shared {@code agentforge4j.llm} model; keeps transport, auth headers,
 * and DTO parsing out of portable core modules. Intended for applications that depend on this module
 * on the module path so {@link LlmClientFactory} implementations are discoverable via {@code ServiceLoader}.
 */
module agentforge4j.llm.openai {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires static lombok;
  requires org.apache.commons.lang3;
  opens com.agentforge4j.llm.openai.dto to com.fasterxml.jackson.databind;
  uses LlmClientFactory;
  provides LlmClientFactory
      with OpenAiLlmClientFactory;
  provides LlmClientConfigurationAdapter
      with OpenAiConfigurationAdapter;
}
