package com.agentforge4j.starter.llmclient.vllm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.vllm")
public record VllmLlmClientProperties(
    String url,
    String defaultModel,
    Duration connectTimeout,
    Duration requestTimeout) {

  public VllmLlmClientProperties {
    if (defaultModel == null) {
      defaultModel = "";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(5);
    }
  }
}
