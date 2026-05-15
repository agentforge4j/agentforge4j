package com.agentforge4j.starter.llmclient.bedrock;

import com.agentforge4j.llm.bedrock.BedrockConfiguration;
import java.time.Duration;

record BedrockConfigurationAdapter(BedrockLlmClientProperties properties)
    implements BedrockConfiguration {

  @Override
  public String getRegion() {
    return properties.region();
  }

  @Override
  public String getDefaultModel() {
    return properties.modelId();
  }

  @Override
  public String getAnthropicVersion() {
    return properties.anthropicVersion();
  }

  @Override
  public Integer getMaxTokens() {
    return properties.maxTokens();
  }

  @Override
  public Double getTemperature() {
    return properties.temperature();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public Duration getRequestTimeout() {
    return properties.requestTimeout();
  }
}
