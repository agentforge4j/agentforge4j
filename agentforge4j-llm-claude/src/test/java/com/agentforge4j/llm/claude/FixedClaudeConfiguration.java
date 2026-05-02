package com.agentforge4j.llm.claude;

import java.time.Duration;

/**
 * Test {@link ClaudeConfiguration} with defaults suitable for automated tests (no real network).
 */
final class FixedClaudeConfiguration implements ClaudeConfiguration {

  private final String url;
  private final String apiKey;
  private final String apiVersion;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final Duration requestTimeout;
  private final int maxTokenSize;

  private FixedClaudeConfiguration(Builder b) {
    this.url = b.url;
    this.apiKey = b.apiKey;
    this.apiVersion = b.apiVersion;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.requestTimeout = b.requestTimeout;
    this.maxTokenSize = b.maxTokenSize;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedClaudeConfiguration defaults() {
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
  public String getApiKey() {
    return apiKey;
  }

  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  @Override
  public int getMaxTokenSize() {
    return maxTokenSize;
  }

  static final class Builder {
    private String url = "https://api.anthropic.test/v1/messages";
    private String apiKey = "test-api-key";
    private String apiVersion = "2023-06-01";
    private String defaultModel = "claude-3-opus-20240229";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private int maxTokenSize = 1024;

    Builder url(String url) {
      this.url = url;
      return this;
    }

    Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    Builder apiVersion(String apiVersion) {
      this.apiVersion = apiVersion;
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

    Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    Builder maxTokenSize(int maxTokenSize) {
      this.maxTokenSize = maxTokenSize;
      return this;
    }

    FixedClaudeConfiguration build() {
      return new FixedClaudeConfiguration(this);
    }
  }
}
