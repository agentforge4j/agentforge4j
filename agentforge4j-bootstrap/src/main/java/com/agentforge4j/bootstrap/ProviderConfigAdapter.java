// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.time.Duration;

/**
 * Adapts a {@link LlmProviderConfig} to the {@link LlmClientConfiguration} interface expected by
 * {@link com.agentforge4j.llm.DefaultLlmClientResolver}.
 */
final class ProviderConfigAdapter implements LlmClientConfiguration {

  private final LlmProviderConfig config;

  ProviderConfigAdapter(LlmProviderConfig config) {
    this.config = config;
  }

  @Override
  public String getProviderName() {
    return config.provider();
  }

  @Override
  public String getDefaultModel() {
    return config.defaultModel();
  }

  @Override
  public Duration getConnectTimeout() {
    return config.connectTimeout();
  }
}
