package com.agentforge4j.starter.llmclient.mistral;

import com.agentforge4j.llm.mistral.MistralConfiguration;
import java.time.Duration;

record MistralConfigurationAdapter(MistralLlmClientProperties properties)
    implements MistralConfiguration {

  @Override
  public String getApiKey() {
    return properties.apiKey();
  }

  @Override
  public String getDefaultModel() {
    return properties.defaultModel();
  }

  @Override
  public String getBaseUrl() {
    return properties.baseUrl();
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
