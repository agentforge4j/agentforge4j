// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openaicompat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.openai-compatible.*} for OpenAI Responses-compatible gateways.
 *
 * <p>Endpoint identity, authentication header fields, and {@code responsesPath} must be supplied by
 * application configuration (see individual {@code agentforge4j.llm.openai-compatible.*} keys).
 *
 * @param apiKey credential combined with {@code authHeaderName} and {@code authHeaderPrefix}
 * @param defaultModel model token passed when upstream calls omit one
 * @param baseUrl scheme plus host (and optional port) without a trailing slash
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 * @param authHeaderName HTTP header carrying credentials
 * @param authHeaderPrefix literal prefix before the key (may be an empty string)
 * @param responsesPath REST path segment for the Responses endpoint, starting with {@code /}
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

  /** Applies non-null HTTP timeouts only; all other fields are forwarded verbatim. */
  public OpenAiCompatibleLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
