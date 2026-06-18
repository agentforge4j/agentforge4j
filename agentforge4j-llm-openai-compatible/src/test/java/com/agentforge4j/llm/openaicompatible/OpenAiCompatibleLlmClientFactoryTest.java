// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnOpenaiCompatible() {
      OpenAiCompatibleLlmClientFactory factory = new OpenAiCompatibleLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("openai-compatible");
    }
  }

  @Nested
  class CreateFromContextTests {

    private static final LlmSecretResolver RESOLVER =
        reference -> new LlmSecret(reference.literalValue());

    @Test
    void shouldCreateClientFromNeutralContext() {
      LlmClientFactoryContext context = new LlmClientFactoryContext(
          new ObjectMapper(), neutralConfig(true, true), RESOLVER);

      LlmClient client = new OpenAiCompatibleLlmClientFactory().create(context);

      assertThat(client).isInstanceOf(OpenAiCompatibleLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("openai-compatible");
    }

    @Test
    void shouldThrowWhenApiKeyReferenceMissing() {
      LlmClientFactoryContext context = new LlmClientFactoryContext(
          new ObjectMapper(), neutralConfig(false, true), RESOLVER);

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClientFactory().create(context))
          .isInstanceOf(LlmProviderConfigurationException.class)
          .hasMessageContaining("openai-compatible")
          .hasMessageContaining("API key");
    }

    @Test
    void shouldThrowWhenRequiredOptionMissing() {
      LlmClientFactoryContext context = new LlmClientFactoryContext(
          new ObjectMapper(), neutralConfig(true, false), RESOLVER);

      assertThatThrownBy(() -> new OpenAiCompatibleLlmClientFactory().create(context))
          .isInstanceOf(LlmProviderConfigurationException.class)
          .hasMessageContaining("responses.path");
    }

    private static LlmClientConfiguration neutralConfig(boolean withApiKey,
        boolean withResponsesPath) {
      Map<String, String> options = new HashMap<>();
      options.put("auth.header.name", "Authorization");
      options.put("auth.header.prefix", "Bearer ");
      options.put("request.timeout", "PT30S");
      if (withResponsesPath) {
        options.put("responses.path", "/v1/responses");
      }
      return new LlmClientConfiguration() {
        @Override
        public String getProviderName() {
          return "openai-compatible";
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
          return "https://api.example.com";
        }

        @Override
        public Optional<LlmSecretReference> getApiKeyReference() {
          return withApiKey ? Optional.of(LlmSecretReference.literal("sk-test")) : Optional.empty();
        }

        @Override
        public LlmProviderOptions getOptions() {
          return LlmProviderOptions.of("openai-compatible", options);
        }
      };
    }
  }
}
