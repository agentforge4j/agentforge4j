package com.agentforge4j.starter.llmclient.bedrock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.llm.bedrock.*} for Anthropic models exposed through Bedrock Runtime.
 *
 * @param enabled opt-in evaluated by {@linkplain BedrockProviderAutoConfiguration}
 * @param region AWS region id used for endpoint resolution and SigV4 signing
 * @param modelId Bedrock model identifier (Anthropic-only in the current adapter)
 * @param anthropicVersion request payload version string understood by Bedrock
 * @param maxTokens generation cap forwarded when non-null; otherwise the client omits the field
 * @param temperature sampling parameter forwarded when non-null
 * @param connectTimeout initialized to ten seconds when {@code null}
 * @param requestTimeout initialized to two minutes when {@code null}
 */
@ConfigurationProperties(prefix = "agentforge4j.llm.bedrock")
public record BedrockLlmClientProperties(
    Boolean enabled,
    String region,
    String modelId,
    String anthropicVersion,
    Integer maxTokens,
    Double temperature,
    Duration connectTimeout,
    Duration requestTimeout) {

  /** Supplies minimum HTTP timeout guarantees for Bedrock SDK calls. */
  public BedrockLlmClientProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(10);
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofMinutes(2);
    }
  }
}
