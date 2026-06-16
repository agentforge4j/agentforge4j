// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.claude;

import com.agentforge4j.llm.claude.ClaudeConfiguration;
import com.agentforge4j.util.Validate;
import java.time.Duration;

record ClaudeConfigurationAdapter(ClaudeLlmClientProperties properties)
    implements ClaudeConfiguration {

  @Override
  public String getApiKey() {
    return properties.apiKey();
  }

  @Override
  public String getDefaultModel() {
    return properties.defaultModel();
  }

  @Override
  public String getApiVersion() {
    return properties.apiVersion();
  }

  @Override
  public String getUrl() {
    return properties.url();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public Duration getRequestTimeout() {
    return properties.requestTimeout();
  }

  @Override
  public int getMaxTokenSize() {
    return Validate.notNull(
        properties.maxTokenSize(),
        "agentforge4j.llm.claude.max-token-size must be set").intValue();
  }
}
