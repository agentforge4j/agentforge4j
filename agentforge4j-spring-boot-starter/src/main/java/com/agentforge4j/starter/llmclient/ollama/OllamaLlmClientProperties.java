package com.agentforge4j.starter.llmclient.ollama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.ollama")
public record OllamaLlmClientProperties(
    Boolean enabled,
    String defaultModel,
    String url,
    Duration connectTimeout,
    Duration requestTimeout) {

  public OllamaLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(5);
    }
  }
}
