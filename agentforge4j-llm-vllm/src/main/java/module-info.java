// SPDX-License-Identifier: Apache-2.0
import com.agentforge4j.llm.LlmClientConfigurationAdapter;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.vllm.VllmConfigurationAdapter;
import com.agentforge4j.llm.vllm.VllmLlmClientFactory;

/**
 * vLLM OpenAI-compatible server adapter exposed via {@link LlmClientFactory}.
 *
 * <p>Bridges self-hosted high-throughput inference deployments to the shared LLM client contract
 * without coupling workflow definitions to a particular hosting topology.
 */
module agentforge4j.llm.vllm {
  requires agentforge4j.llm;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires org.apache.commons.lang3;
  requires static lombok;
  uses com.agentforge4j.llm.LlmClientFactory;
  opens com.agentforge4j.llm.vllm.dto to com.fasterxml.jackson.databind;
  provides LlmClientFactory
      with VllmLlmClientFactory;
  provides LlmClientConfigurationAdapter
      with VllmConfigurationAdapter;
}
