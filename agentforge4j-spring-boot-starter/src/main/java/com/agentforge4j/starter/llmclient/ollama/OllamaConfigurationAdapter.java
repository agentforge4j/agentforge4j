package com.agentforge4j.starter.llmclient.ollama;

import com.agentforge4j.llm.ollama.OllamaConfiguration;
import java.time.Duration;

record OllamaConfigurationAdapter(OllamaLlmClientProperties properties)
    implements OllamaConfiguration {

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
