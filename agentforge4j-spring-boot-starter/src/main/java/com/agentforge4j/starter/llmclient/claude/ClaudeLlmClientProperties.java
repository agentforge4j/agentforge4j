package com.agentforge4j.starter.llmclient.claude;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.claude.*} for Anthropic's Claude HTTP API.
 *
 * @param apiKey credential header content
 * @param defaultModel model handle used when requests supply none
 * @param apiVersion optional API version override understood by the Claude client
 * @param url HTTPS endpoint; {@code null} keeps the module default base URL
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.claude")
public record ClaudeLlmClientProperties(
    String apiKey,
    String defaultModel,
    String apiVersion,
    String url,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Applies Claude module defaults for timeouts. */
  public ClaudeLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
