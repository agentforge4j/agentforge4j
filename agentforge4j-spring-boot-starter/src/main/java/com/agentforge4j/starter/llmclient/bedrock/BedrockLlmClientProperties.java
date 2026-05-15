package com.agentforge4j.starter.llmclient.bedrock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.bedrock")
public record BedrockLlmClientProperties(
    Boolean enabled,
    String region,
    String modelId,
    String anthropicVersion,
    Integer maxTokens,
    Double temperature,
    Duration connectTimeout,
    Duration requestTimeout) {

  public BedrockLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
