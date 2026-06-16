// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

import java.time.Duration;

/**
 * Test {@link MistralConfiguration} with defaults suitable for offline tests (no real network).
 */
final class FixedMistralConfiguration implements MistralConfiguration {

  private final String baseUrl;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final String apiKey;
  private final Duration requestTimeout;

  private FixedMistralConfiguration(Builder b) {
    this.baseUrl = b.baseUrl;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.apiKey = b.apiKey;
    this.requestTimeout = b.requestTimeout;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedMistralConfiguration defaults() {
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
  public String getBaseUrl() {
    return baseUrl;
  }

  @Override
  public String getApiKey() {
    return apiKey;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  static final class Builder {
    private String baseUrl = "https://api.mistral.ai";
    private String defaultModel = "mistral-small-latest";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private String apiKey = "test-key";
    private Duration requestTimeout = Duration.ofSeconds(30);

    Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
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

    Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    FixedMistralConfiguration build() {
      return new FixedMistralConfiguration(this);
    }
  }
}
