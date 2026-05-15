package com.agentforge4j.starter.llmclient.openaicompat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.openai-compatible")
public record OpenAiCompatibleLlmClientProperties(
    String apiKey,
    String defaultModel,
    String baseUrl,
    Duration connectTimeout,
    Duration requestTimeout,
    String authHeaderName,
    String authHeaderPrefix,
    String responsesPath) {

  public OpenAiCompatibleLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
    if (authHeaderName == null || authHeaderName.isBlank()) {
      authHeaderName = "Authorization";
    }
    if (authHeaderPrefix == null) {
      authHeaderPrefix = "Bearer ";
    }
  }
}
