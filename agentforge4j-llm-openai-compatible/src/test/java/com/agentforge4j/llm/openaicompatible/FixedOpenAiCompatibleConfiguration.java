package com.agentforge4j.llm.openaicompatible;

import java.time.Duration;

/**
 * Test configuration with defaults suitable for automated tests (no real network).
 */
final class FixedOpenAiCompatibleConfiguration implements OpenAiCompatibleConfiguration {

  private final String baseUrl;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final String apiKey;
  private final Duration requestTimeout;
  private final String authHeaderName;
  private final String authHeaderPrefix;
  private final String responsesPath;

  private FixedOpenAiCompatibleConfiguration(Builder b) {
    this.baseUrl = b.baseUrl;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.apiKey = b.apiKey;
    this.requestTimeout = b.requestTimeout;
    this.authHeaderName = b.authHeaderName;
    this.authHeaderPrefix = b.authHeaderPrefix;
    this.responsesPath = b.responsesPath;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedOpenAiCompatibleConfiguration defaults() {
    return builder().build();
  }

  @Override
  public String getBaseUrl() {
    return baseUrl;
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

  @Override
  public String getAuthHeaderName() {
    return authHeaderName;
  }

  @Override
  public String getAuthHeaderPrefix() {
    return authHeaderPrefix;
  }

  @Override
  public String getResponsesPath() {
    return responsesPath != null ? responsesPath : OpenAiCompatibleConfiguration.super.getResponsesPath();
  }

  static final class Builder {
    private String baseUrl = "http://127.0.0.1:9";
    private String defaultModel = "mistral";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private String apiKey = "test-key";
    private Duration requestTimeout = Duration.ofSeconds(30);
    private String authHeaderName = "Authorization";
    private String authHeaderPrefix = "Bearer ";
    private String responsesPath;

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

    Builder authHeaderName(String authHeaderName) {
      this.authHeaderName = authHeaderName;
      return this;
    }

    Builder authHeaderPrefix(String authHeaderPrefix) {
      this.authHeaderPrefix = authHeaderPrefix;
      return this;
    }

    /**
     * When {@code null}, {@link OpenAiCompatibleConfiguration#getResponsesPath()} default applies.
     */
    Builder responsesPath(String responsesPath) {
      this.responsesPath = responsesPath;
      return this;
    }

    FixedOpenAiCompatibleConfiguration build() {
      return new FixedOpenAiCompatibleConfiguration(this);
    }
  }
}
