// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.openaicompatible.OpenAiCompatibleConfigurationAdapter;
import com.agentforge4j.llm.openaicompatible.OpenAiCompatibleLlmClientFactory;

/**
 * Generic OpenAI-compatible HTTP surface (local gateways, proxies, alternate hosts) as {@link LlmClientFactory}.
 *
 * <p>Use when the remote speaks OpenAI-style paths and payloads but is not the official OpenAI API;
 * keeps compatibility shims out of the first-party OpenAI module.
 */
module agentforge4j.llm.openaicompatible {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires static lombok;
  requires org.apache.commons.lang3;
  requires java.net.http;
  opens com.agentforge4j.llm.openaicompatible.dto to com.fasterxml.jackson.databind;
  uses LlmClientFactory;
  provides LlmClientFactory
    with OpenAiCompatibleLlmClientFactory;
  provides LlmClientConfigurationAdapter
    with OpenAiCompatibleConfigurationAdapter;
}
