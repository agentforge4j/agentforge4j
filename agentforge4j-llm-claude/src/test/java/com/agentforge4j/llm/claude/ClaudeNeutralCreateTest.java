// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.LlmSecretResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeNeutralCreateTest {

  private static final LlmSecretResolver RESOLVER = reference -> new LlmSecret(reference.literalValue());

  @Test
  void buildsClientFromNeutralContext() {
    LlmClient client = new ClaudeLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(true), RESOLVER));

    assertThat(client).isInstanceOf(ClaudeLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("claude");
  }

  @Test
  void failsWhenMaxTokenSizeMissing() {
    assertThatThrownBy(() -> new ClaudeLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(false), RESOLVER)))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("max.token.size");
  }

  @Test
  void defaultsRequestTimeoutToTwoMinutesWhenAbsent() {
    ClaudeNeutralConfiguration config = ClaudeNeutralConfiguration.fromNeutral(
        neutralWithoutRequestTimeout(), new LlmSecret("ant-test"));

    assertThat(config.getRequestTimeout()).isEqualTo(ClaudeDefaults.REQUEST_TIMEOUT);
  }

  private static LlmClientConfiguration neutral(boolean withMaxTokenSize) {
    Map<String, String> options = new HashMap<>();
    options.put("api.version", "2023-06-01");
    options.put("request.timeout", "PT30S");
    if (withMaxTokenSize) {
      options.put("max.token.size", "4096");
    }
    return config(options);
  }

  private static LlmClientConfiguration neutralWithoutRequestTimeout() {
    Map<String, String> options = new HashMap<>();
    options.put("api.version", "2023-06-01");
    options.put("max.token.size", "4096");
    return config(options);
  }

  private static LlmClientConfiguration config(Map<String, String> options) {
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "claude";
      }

      @Override
      public String getDefaultModel() {
        return "claude-3-5-sonnet";
      }

      @Override
      public Duration getConnectTimeout() {
        return Duration.ofSeconds(10);
      }

      @Override
      public String getBaseUrl() {
        return "https://api.anthropic.com";
      }

      @Override
      public Optional<LlmSecretReference> getApiKeyReference() {
        return Optional.of(LlmSecretReference.literal("ant-test"));
      }

      @Override
      public LlmProviderOptions getOptions() {
        return LlmProviderOptions.of("claude", options);
      }
    };
  }
}
