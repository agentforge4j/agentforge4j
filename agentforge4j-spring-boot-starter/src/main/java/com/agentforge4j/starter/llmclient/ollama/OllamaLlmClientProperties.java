// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.ollama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.ollama.*} for local or remote {@code Ollama} servers.
 *
 * @param enabled opt-in toggle evaluated by {@linkplain OllamaProviderAutoConfiguration}
 * @param defaultModel inference model slug passed when requests omit overrides
 * @param url REST base ({@code http://host:11434}); {@code null} falls back inside the adapter
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to five minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.ollama")
public record OllamaLlmClientProperties(
    Boolean enabled,
    String defaultModel,
    String url,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Applies non-null HTTP timeouts consumed by {@code Ollama} clients. */
  public OllamaLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(5);
    }
  }
}
