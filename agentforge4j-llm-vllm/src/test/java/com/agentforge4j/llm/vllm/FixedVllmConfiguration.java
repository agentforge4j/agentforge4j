// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import java.time.Duration;

/**
 * Test configuration with defaults suitable for automated tests (no real network).
 */
final class FixedVllmConfiguration implements VllmConfiguration {

  private final String url;
  private final String defaultModel;
  private final Duration connectTimeout;
  private final Duration requestTimeout;

  private FixedVllmConfiguration(Builder b) {
    this.url = b.url;
    this.defaultModel = b.defaultModel;
    this.connectTimeout = b.connectTimeout;
    this.requestTimeout = b.requestTimeout;
  }

  static Builder builder() {
    return new Builder();
  }

  static FixedVllmConfiguration defaults() {
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
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  static final class Builder {
    private String url = "http://127.0.0.1:9/v1/chat/completions";
    private String defaultModel = "meta-llama/Llama-2-7b-chat-hf";
    private Duration connectTimeout = Duration.ofSeconds(10);
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

    Builder requestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
    }

    FixedVllmConfiguration build() {
      return new FixedVllmConfiguration(this);
    }
  }
}
