// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * Factory for creating vLLM LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide vLLM-specific client instances.
 */
public final class VllmLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for vLLM.
   *
   * @return "vllm"
   */
  @Override
  public String getProviderName() {
    return "vllm";
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  /**
   * Creates a vLLM client from a neutral {@link LlmClientFactoryContext}: maps the neutral configuration and provider
   * options into the validated {@link VllmConfiguration}. vLLM requires no credential.
   *
   * @param context the factory inputs
   *
   * @return a new vLLM LLM client
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    VllmConfiguration config = VllmNeutralConfiguration.fromNeutral(context.configuration());
    return new VllmLlmClient(context.objectMapper(), config);
  }
}
