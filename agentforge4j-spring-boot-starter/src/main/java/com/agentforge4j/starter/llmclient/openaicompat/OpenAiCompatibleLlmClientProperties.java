package com.agentforge4j.starter.llmclient.openaicompat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.openai-compatible.*} for OpenAI Responses-compatible gateways.
 *
 * @param apiKey credential combined with {@code authHeaderName} and {@code authHeaderPrefix}
 * @param defaultModel model token passed when upstream calls omit one
 * @param baseUrl scheme plus host (and optional port) without a trailing slash
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 * @param authHeaderName HTTP header carrying credentials (defaults to {@code Authorization})
 * @param authHeaderPrefix literal prefix before the key (defaults to {@code Bearer })
 * @param responsesPath override for the REST path segment; {@code null} is forwarded unchanged to
 *     the adapter
 */
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

  /** Fills timeouts and sane authentication header defaults. */
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
