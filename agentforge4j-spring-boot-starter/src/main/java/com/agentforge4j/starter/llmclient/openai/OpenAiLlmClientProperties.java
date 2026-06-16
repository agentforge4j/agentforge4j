// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.openai.*} for OpenAI-hosted models.
 *
 * @param apiKey Bearer credential sent to OpenAI-compatible endpoints
 * @param defaultModel model id injected when callers omit explicit model metadata
 * @param url HTTPS base endpoint; {@code null} selects the client's built-in production host
 * @param connectTimeout HTTP connect-phase limit; initialized to ten seconds when {@code null}
 * @param requestTimeout full-request limit; initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.openai")
public record OpenAiLlmClientProperties(
    String apiKey,
    String defaultModel,
    String url,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Applies non-null timeouts required by the downstream HTTP adapter. */
  public OpenAiLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
