// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Internal {@link BedrockConfiguration} built from a neutral {@link LlmClientConfiguration}.
 *
 * <p>Bedrock authenticates through the AWS SDK rather than a bearer key, so there is no credential
 * reference. This neutral form leaves {@link #getCredentialsProvider()} and {@link #getEndpointOverride()} at their
 * {@code null} defaults, so production resolves credentials via the AWS default provider chain and the standard
 * regional endpoint (see {@code BedrockRuntimeClientFactory}). Those overrides remain available only to programmatic /
 * test wiring that constructs a {@link BedrockConfiguration} directly.
 *
 * @param defaultModel     the default model id
 * @param connectTimeout   the HTTP connect timeout
 * @param region           the AWS region
 * @param anthropicVersion the Bedrock Anthropic API version
 * @param requestTimeout   the request timeout
 * @param maxTokens        the maximum tokens to generate, or {@code null}
 * @param temperature      the sampling temperature, or {@code null}
 */
record BedrockNeutralConfiguration(
    String defaultModel,
    Duration connectTimeout,
    String region,
    String anthropicVersion,
    Duration requestTimeout,
    Integer maxTokens,
    Double temperature) implements BedrockConfiguration {

  /**
   * Maps a neutral configuration into this provider's validated form. No credential is resolved: Bedrock uses the AWS
   * default credentials chain.
   *
   * <p>An absent {@code request.timeout} option defaults to {@link BedrockDefaults#REQUEST_TIMEOUT} — the same value
   * {@link BedrockConfigurationAdapter} applies for the properties-configured path — so the two construction paths
   * cannot silently diverge on what "the" default is.
   *
   * @param neutral the neutral provider configuration
   *
   * @return the validated configuration
   *
   * @throws com.agentforge4j.llm.LlmProviderConfigurationException if a required value is missing or invalid
   */
  static BedrockNeutralConfiguration fromNeutral(LlmClientConfiguration neutral) {
    Validate.notNull(neutral, "neutral configuration must not be null");
    LlmProviderOptions options = neutral.getOptions();
    return new BedrockNeutralConfiguration(
        neutral.getDefaultModel(),
        neutral.getConnectTimeout(),
        options.requireString("region"),
        options.requireString("anthropic.version"),
        options.duration("request.timeout").orElse(BedrockDefaults.REQUEST_TIMEOUT),
        options.integer("max.tokens").orElse(null),
        options.decimal("temperature").orElse(null));
  }

  @Override
  public String getDefaultModel() {
    return defaultModel;
  }

  @Override
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public String getAnthropicVersion() {
    return anthropicVersion;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  @Override
  public Integer getMaxTokens() {
    return maxTokens;
  }

  @Override
  public Double getTemperature() {
    return temperature;
  }
}
