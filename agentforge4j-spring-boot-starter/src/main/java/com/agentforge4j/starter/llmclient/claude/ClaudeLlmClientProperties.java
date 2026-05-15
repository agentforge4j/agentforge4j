package com.agentforge4j.starter.llmclient.claude;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.claude")
public record ClaudeLlmClientProperties(
    String apiKey,
    String defaultModel,
    String apiVersion,
    String url,
    Duration connectTimeout,
    Duration requestTimeout) {

  public ClaudeLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
