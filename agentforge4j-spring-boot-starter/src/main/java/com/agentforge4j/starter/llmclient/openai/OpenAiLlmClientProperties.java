package com.agentforge4j.starter.llmclient.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.openai")
public record OpenAiLlmClientProperties(
    String apiKey,
    String defaultModel,
    String url,
    Duration connectTimeout,
    Duration requestTimeout) {

  public OpenAiLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
