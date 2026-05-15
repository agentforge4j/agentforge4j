package com.agentforge4j.starter.llmclient.openai;

import com.agentforge4j.llm.openai.OpenAiConfiguration;
import java.time.Duration;

record OpenAiConfigurationAdapter(OpenAiLlmClientProperties properties)
    implements OpenAiConfiguration {

  @Override
  public String getApiKey() {
    return properties.apiKey();
  }

  @Override
  public String getDefaultModel() {
    return properties.defaultModel();
  }

  @Override
  public String getUrl() {
    return properties.url();
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
