package com.agentforge4j.starter.llmclient.azureopenai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentforge4j.llm.azure-openai")
public record AzureOpenAiLlmClientProperties(
    String apiKey,
    String deploymentName,
    String endpoint,
    String apiVersion,
    Duration connectTimeout,
    Duration requestTimeout) {

  public AzureOpenAiLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
