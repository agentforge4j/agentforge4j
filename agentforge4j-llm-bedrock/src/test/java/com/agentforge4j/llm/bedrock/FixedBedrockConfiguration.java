// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

final class FixedBedrockConfiguration implements BedrockConfiguration {

  private final String region;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final Duration requestTimeout;
  private final String anthropicVersion;
  private final Integer maxTokens;
  private final Double temperature;
  private final URI endpointOverride;
  private final AwsCredentialsProvider credentialsProvider;

  private FixedBedrockConfiguration(Builder b) {
    this.region = b.region;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.requestTimeout = b.requestTimeout;
    this.anthropicVersion = b.anthropicVersion;
    this.maxTokens = b.maxTokens;
    this.temperature = b.temperature;
    this.endpointOverride = b.endpointOverride;
    this.credentialsProvider = b.credentialsProvider;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedBedrockConfiguration defaults() {
    return builder().build();
  }

  @Override
  public String getRegion() {
    return region;
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
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  @Override
  public String getAnthropicVersion() {
    return anthropicVersion;
  }

  @Override
  public Integer getMaxTokens() {
    return maxTokens;
  }

  @Override
  public Double getTemperature() {
    return temperature;
  }

  @Override
  public URI getEndpointOverride() {
    return endpointOverride;
  }

  @Override
  public AwsCredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  static final class Builder {
    private String region = "eu-west-1";
    private String defaultModel = "anthropic.claude-3-haiku-20240307-v1:0";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofMinutes(2);
    private String anthropicVersion = "bedrock-2023-05-31";
    private Integer maxTokens;
    private Double temperature;
    private URI endpointOverride;
    private AwsCredentialsProvider credentialsProvider;

    Builder region(String v) {
      this.region = v;
      return this;
    }

    Builder defaultModel(String v) {
      this.defaultModel = v;
      return this;
    }

    Builder connectTimeout(Duration v) {
      this.connectTimeout = v;
      return this;
    }

    Builder requestTimeout(Duration v) {
      this.requestTimeout = v;
      return this;
    }

    Builder anthropicVersion(String v) {
      this.anthropicVersion = v;
      return this;
    }

    Builder maxTokens(Integer v) {
      this.maxTokens = v;
      return this;
    }

    Builder temperature(Double v) {
      this.temperature = v;
      return this;
    }

    Builder endpointOverride(URI v) {
      this.endpointOverride = v;
      return this;
    }

    Builder credentialsProvider(AwsCredentialsProvider v) {
      this.credentialsProvider = v;
      return this;
    }

    FixedBedrockConfiguration build() {
      return new FixedBedrockConfiguration(this);
    }
  }
}
