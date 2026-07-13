// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnOllama() {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("ollama");
    }
  }

  @Nested
  class CreateFromContextTests {

    @Test
    void shouldCreateClientFromNeutralContext() {
      LlmClientFactoryContext context = new LlmClientFactoryContext(
          new ObjectMapper(), neutralConfig(true, true), reference -> new LlmSecret("unused"));

      LlmClient client = new OllamaLlmClientFactory().create(context);

      assertThat(client).isInstanceOf(OllamaLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("ollama");
    }

    @Test
    void shouldThrowWhenBaseUrlMissing() {
      LlmClientFactoryContext context = new LlmClientFactoryContext(
          new ObjectMapper(), neutralConfig(false, true), reference -> new LlmSecret("unused"));

      assertThatThrownBy(() -> new OllamaLlmClientFactory().create(context))
          .isInstanceOf(LlmProviderConfigurationException.class)
          .hasMessageContaining("ollama")
          .hasMessageContaining("base URL");
    }

    @Test
    void defaultsRequestTimeoutToFiveMinutesWhenAbsent() {
      OllamaNeutralConfiguration config = OllamaNeutralConfiguration.fromNeutral(neutralConfig(true, false));

      assertThat(config.getRequestTimeout()).isEqualTo(OllamaDefaults.REQUEST_TIMEOUT);
    }

    private static LlmClientConfiguration neutralConfig(boolean withBaseUrl, boolean withTimeout) {
      Map<String, String> options = new HashMap<>();
      if (withTimeout) {
        options.put("request.timeout", "PT30S");
      }
      return new LlmClientConfiguration() {
        @Override
        public String getProviderName() {
          return "ollama";
        }

        @Override
        public String getDefaultModel() {
          return "llama2";
        }

        @Override
        public Duration getConnectTimeout() {
          return Duration.ofSeconds(10);
        }

        @Override
        public String getBaseUrl() {
          return withBaseUrl ? "http://localhost:11434/api/chat" : null;
        }

        @Override
        public LlmProviderOptions getOptions() {
          return LlmProviderOptions.of("ollama", options);
        }
      };
    }
  }
}

