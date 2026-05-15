package com.agentforge4j.starter.llmclient.gemini;

import com.agentforge4j.llm.gemini.GeminiConfiguration;
import java.time.Duration;

record GeminiConfigurationAdapter(GeminiLlmClientProperties properties)
    implements GeminiConfiguration {

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
