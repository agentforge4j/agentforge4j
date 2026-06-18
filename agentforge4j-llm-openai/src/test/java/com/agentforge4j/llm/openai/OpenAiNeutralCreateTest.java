// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

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

class OpenAiNeutralCreateTest {

  private static final LlmSecretResolver RESOLVER = reference -> new LlmSecret(reference.literalValue());

  @Test
  void buildsClientFromNeutralContext() {
    LlmClient client = new OpenAiLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(true), RESOLVER));

    assertThat(client).isInstanceOf(OpenAiLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("openai");
  }

  @Test
  void defaultsRequestTimeoutToThirtySecondsWhenAbsent() {
    OpenAiNeutralConfiguration config = OpenAiNeutralConfiguration.fromNeutral(
        neutralWithoutRequestTimeout(), new LlmSecret("sk-test"));

    assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void failsWhenBaseUrlMissing() {
    assertThatThrownBy(() -> new OpenAiLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(false), RESOLVER)))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("openai")
        .hasMessageContaining("base URL");
  }

  private static LlmClientConfiguration neutral(boolean withBaseUrl) {
    Map<String, String> options = new HashMap<>();
    options.put("request.timeout", "PT30S");
    return config(withBaseUrl, options);
  }

  private static LlmClientConfiguration neutralWithoutRequestTimeout() {
    return config(true, new HashMap<>());
  }

  private static LlmClientConfiguration config(boolean withBaseUrl, Map<String, String> options) {
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "openai";
      }

      @Override
      public String getDefaultModel() {
        return "gpt-4o";
      }

      @Override
      public Duration getConnectTimeout() {
        return Duration.ofSeconds(10);
      }

      @Override
      public String getBaseUrl() {
        return withBaseUrl ? "https://api.openai.com/v1/responses" : null;
      }

      @Override
      public Optional<LlmSecretReference> getApiKeyReference() {
        return Optional.of(LlmSecretReference.literal("sk-test"));
      }

      @Override
      public LlmProviderOptions getOptions() {
        return LlmProviderOptions.of("openai", options);
      }
    };
  }
}
