// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Factory for creating Amazon Bedrock LLM clients.
 * <p>
 * Discovered via JPMS ServiceLoader to provide Bedrock-specific client instances.
 */
public final class BedrockLlmClientFactory implements LlmClientFactory {

  /**
   * Returns the provider name for Bedrock.
   *
   * @return "bedrock"
   */
  @Override
  public String getProviderName() {
    return "bedrock";
  }

  /**
   * Bedrock authenticates through the AWS SDK (default credentials chain), not a bearer API key.
   *
   * @return {@code false}
   */
  @Override
  public boolean requiresApiKey() {
    return false;
  }

  /**
   * Creates a Bedrock client from a neutral {@link LlmClientFactoryContext}: maps the neutral
   * configuration and provider options into the validated {@link BedrockConfiguration}. No credential
   * is resolved — Bedrock uses the AWS default credentials chain and the standard regional endpoint
   * (endpoint/credential overrides are available only to direct programmatic wiring).
   *
   * @param context the factory inputs
   * @return a new Bedrock LLM client
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or
   *                                                               invalid
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    BedrockConfiguration config = BedrockNeutralConfiguration.fromNeutral(context.configuration());
    BedrockRuntimeClient client = BedrockRuntimeClientFactory.create(config);
    return new BedrockLlmClient(context.objectMapper(), config, client);
  }
}
