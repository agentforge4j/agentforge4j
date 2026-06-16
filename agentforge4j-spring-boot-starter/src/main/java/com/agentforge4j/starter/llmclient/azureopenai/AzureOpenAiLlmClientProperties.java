// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.azureopenai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.azure-openai.*} for Azure OpenAI deployments.
 *
 * @param apiKey key header material for Azure token auth
 * @param deploymentName Azure deployment identifier mapped to default model selection
 * @param endpoint regional resource URL such as {@code https://{resource}.openai.azure.com}
 * @param apiVersion query parameter string required by Azure's REST contract
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.azure-openai")
public record AzureOpenAiLlmClientProperties(
    String apiKey,
    String deploymentName,
    String endpoint,
    String apiVersion,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Normalizes HTTP timeouts consumed by the Azure OpenAI client. */
  public AzureOpenAiLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
