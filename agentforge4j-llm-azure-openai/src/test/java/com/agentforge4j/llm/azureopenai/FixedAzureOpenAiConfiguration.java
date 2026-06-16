// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

import java.time.Duration;

/**
 * Test configuration with defaults suitable for unit tests (no real network).
 */
final class FixedAzureOpenAiConfiguration implements AzureOpenAiConfiguration {

  private final String endpoint;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final String apiKey;
  private final String deploymentName;
  private final String apiVersion;
  private final Duration requestTimeout;

  private FixedAzureOpenAiConfiguration(Builder b) {
    this.endpoint = b.endpoint;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.apiKey = b.apiKey;
    this.deploymentName = b.deploymentName;
    this.apiVersion = b.apiVersion;
    this.requestTimeout = b.requestTimeout;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedAzureOpenAiConfiguration defaults() {
    return builder().build();
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
  public String getEndpoint() {
    return endpoint;
  }

  @Override
  public String getApiKey() {
    return apiKey;
  }

  @Override
  public String getDeploymentName() {
    return deploymentName;
  }

  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  static final class Builder {
    private String endpoint = "https://test.openai.azure.com";
    private String defaultModel = "gpt-4";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private String apiKey = "test-key";
    private String deploymentName = "gpt-4-deployment";
    private String apiVersion = "2024-01-01";
    private Duration requestTimeout = Duration.ofSeconds(30);

    Builder endpoint(String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    Builder defaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
      return this;
    }

    Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    Builder deploymentName(String deploymentName) {
      this.deploymentName = deploymentName;
      return this;
    }

    Builder apiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
      return this;
    }

    Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    FixedAzureOpenAiConfiguration build() {
      return new FixedAzureOpenAiConfiguration(this);
    }
  }
}
