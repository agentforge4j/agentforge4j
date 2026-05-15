package com.agentforge4j.starter.llmclient.openaicompat;

import com.agentforge4j.llm.openaicompatible.OpenAiCompatibleConfiguration;
import java.time.Duration;

record OpenAiCompatibleConfigurationAdapter(OpenAiCompatibleLlmClientProperties properties)
    implements OpenAiCompatibleConfiguration {

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

  @Override
  public String getAuthHeaderName() {
    return properties.authHeaderName();
  }

  @Override
  public String getAuthHeaderPrefix() {
    return properties.authHeaderPrefix();
  }

  @Override
  public String getResponsesPath() {
    return properties.responsesPath();
  }
}
