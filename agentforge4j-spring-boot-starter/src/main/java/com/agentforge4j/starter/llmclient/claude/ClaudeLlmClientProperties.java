package com.agentforge4j.starter.llmclient.claude;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.claude.*} for Anthropic's Claude HTTP API.
 *
 * @param apiKey credential header content
 * @param defaultModel model handle used when requests supply none
 * @param apiVersion API version for the Anthropic HTTP API
 * @param url HTTPS endpoint
 * @param maxTokenSize max tokens for Claude requests
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.claude")
public record ClaudeLlmClientProperties(
    String apiKey,
    String defaultModel,
    String apiVersion,
    String url,
    Integer maxTokenSize,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Normalizes HTTP timeouts when unset. */
  public ClaudeLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
