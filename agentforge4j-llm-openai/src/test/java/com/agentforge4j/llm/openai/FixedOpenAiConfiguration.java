// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import java.time.Duration;

/**
 * Test configuration with defaults suitable for automated tests (no real network).
 */
final class FixedOpenAiConfiguration implements OpenAiConfiguration {

  private final String url;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final String apiKey;
  private final Duration requestTimeout;

  private FixedOpenAiConfiguration(Builder b) {
    this.url = b.url;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.apiKey = b.apiKey;
    this.requestTimeout = b.requestTimeout;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedOpenAiConfiguration defaults() {
    return builder().build();
  }

  @Override
  public String getUrl() {
    return url;
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
  public String getApiKey() {
    return apiKey;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  static final class Builder {
    private String url = "https://api.openai.com/v1/responses";
    private String defaultModel = "gpt-4";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private String apiKey = "test-key";
    private Duration requestTimeout = Duration.ofSeconds(30);

    Builder url(String url) {
      this.url = url;
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

    FixedOpenAiConfiguration build() {
      return new FixedOpenAiConfiguration(this);
    }
  }
}
