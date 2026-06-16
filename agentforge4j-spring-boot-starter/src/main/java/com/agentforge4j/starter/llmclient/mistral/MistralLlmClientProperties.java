// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.mistral;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.mistral.*} for Mistral's public API.
 *
 * @param apiKey credential string
 * @param defaultModel routing token when requests lack explicit model metadata
 * @param baseUrl REST origin replacing the module default when non-null
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.mistral")
public record MistralLlmClientProperties(
    String apiKey,
    String defaultModel,
    String baseUrl,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Ensures HTTP timeout fields are non-null for the Mistral client. */
  public MistralLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
