// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.gemini;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.gemini.*} for Google Gemini REST endpoints.
 *
 * @param apiKey Gemini API credential
 * @param defaultModel model resource id when callers omit specificity
 * @param baseUrl service root overriding the client's default multi-tenant host
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.gemini")
public record GeminiLlmClientProperties(
    String apiKey,
    String defaultModel,
    String baseUrl,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Supplies non-null HTTP timeouts required by the Gemini HTTP stack. */
  public GeminiLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
