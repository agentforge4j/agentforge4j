package com.agentforge4j.llm.gemini;

import java.time.Duration;

/**
 * Test configuration for unit tests (no real network).
 */
final class FixedGeminiConfiguration implements GeminiConfiguration {

  private final String apiKey;
  private final String defaultModel;
  private final String baseUrl;
  private final Duration connectTimeout;
  private final Duration requestTimeout;

  private FixedGeminiConfiguration(Builder b) {
    this.apiKey = b.apiKey;
    this.defaultModel = b.defaultModel;
    this.baseUrl = b.baseUrl;
    this.connectTimeout = b.connectTimeout;
    this.requestTimeout = b.requestTimeout;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedGeminiConfiguration defaults() {
    return builder().build();
  }

  @Override
  public String getApiKey() {
    return apiKey;
  }

  @Override
  public String getDefaultModel() {
    return defaultModel;
  }

  @Override
  public String getBaseUrl() {
    return baseUrl;
  }

  @Override
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  static final class Builder {
    private String apiKey = "test-api-key";
    private String defaultModel = "gemini-1.5-flash";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);

    Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    Builder defaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
      return this;
    }

    Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    Builder connectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    FixedGeminiConfiguration build() {
      return new FixedGeminiConfiguration(this);
    }
  }
}
