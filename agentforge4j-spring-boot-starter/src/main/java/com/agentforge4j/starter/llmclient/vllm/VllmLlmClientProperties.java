// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.vllm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.vllm.*} for OpenAI-chat-compatible vLLM servers.
 *
 * @param url reachable HTTP endpoint for completions
 * @param defaultModel logical model name advertised to the client; blank when {@code null} per the
 *     compact constructor
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to five minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.vllm")
public record VllmLlmClientProperties(
    String url,
    String defaultModel,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Normalizes empty model strings and non-null HTTP timeouts. */
  public VllmLlmClientProperties {
    if (defaultModel == null) {
      defaultModel = "";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(5);
    }
  }
}
