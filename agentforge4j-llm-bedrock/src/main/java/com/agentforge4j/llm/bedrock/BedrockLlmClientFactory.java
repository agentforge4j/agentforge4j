package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
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
   * Creates a Bedrock LLM client with the given configuration.
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param config       the configuration, must be an instance of {@link BedrockConfiguration}
   * @return a new Bedrock LLM client
   * @throws IllegalArgumentException if the config is not a BedrockConfiguration
   */
  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "Bedrock configuration must not be null");
    if (!(config instanceof BedrockConfiguration bedrockConfig)) {
      throw new IllegalArgumentException(
          "BedrockLlmClientFactory requires BedrockConfiguration but got: %s".formatted(
              config.getClass().getName()));
    }
    BedrockRuntimeClient client = BedrockRuntimeClientFactory.create(bedrockConfig);
    return new BedrockLlmClient(objectMapper, bedrockConfig, client);
  }
}
