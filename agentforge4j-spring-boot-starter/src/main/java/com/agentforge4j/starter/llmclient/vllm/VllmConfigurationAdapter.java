package com.agentforge4j.starter.llmclient.vllm;

import com.agentforge4j.llm.vllm.VllmConfiguration;
import java.time.Duration;

record VllmConfigurationAdapter(VllmLlmClientProperties properties)
    implements VllmConfiguration {

  @Override
  public String getUrl() {
    return properties.url();
  }

  @Override
  public String getDefaultModel() {
    return properties.defaultModel();
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
