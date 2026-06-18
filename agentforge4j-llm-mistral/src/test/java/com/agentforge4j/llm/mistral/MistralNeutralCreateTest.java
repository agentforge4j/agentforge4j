// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.mistral;

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

class MistralNeutralCreateTest {

  private static final LlmSecretResolver RESOLVER = reference -> new LlmSecret(reference.literalValue());

  @Test
  void buildsClientFromNeutralContext() {
    LlmClient client = new MistralLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(true), RESOLVER));

    assertThat(client).isInstanceOf(MistralLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("mistral");
  }

  @Test
  void failsWhenBaseUrlMissing() {
    assertThatThrownBy(() -> new MistralLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(false), RESOLVER)))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("mistral")
        .hasMessageContaining("base URL");
  }

  private static LlmClientConfiguration neutral(boolean withBaseUrl) {
    Map<String, String> options = new HashMap<>();
    options.put("request.timeout", "PT30S");
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "mistral";
      }

      @Override
      public String getDefaultModel() {
        return "mistral-large-latest";
      }

      @Override
      public Duration getConnectTimeout() {
        return Duration.ofSeconds(10);
      }

      @Override
      public String getBaseUrl() {
        return withBaseUrl ? "https://api.mistral.ai" : null;
      }

      @Override
      public Optional<LlmSecretReference> getApiKeyReference() {
        return Optional.of(LlmSecretReference.literal("ms-test"));
      }

      @Override
      public LlmProviderOptions getOptions() {
        return LlmProviderOptions.of("mistral", options);
      }
    };
  }
}
