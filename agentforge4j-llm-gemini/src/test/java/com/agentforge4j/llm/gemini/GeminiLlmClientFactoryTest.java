package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnGemini() {
      assertThat(new GeminiLlmClientFactory().getProviderName()).isEqualTo("gemini");
    }
  }

  @Nested
  class CreateTests {

    @Test
    void shouldCreateGeminiLlmClient() {
      ObjectMapper mapper = new ObjectMapper();
      LlmClient client = new GeminiLlmClientFactory().create(mapper,
          FixedGeminiConfiguration.defaults());

      assertThat(client).isInstanceOf(GeminiLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("gemini");
    }

    @Test
    void shouldThrowWhenConfigurationNull() {
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> new GeminiLlmClientFactory().create(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Gemini configuration must not be null");
    }

    @Test
    void shouldThrowWhenConfigIsNotGeminiConfiguration() {
      ObjectMapper mapper = new ObjectMapper();
      LlmClientConfiguration invalidConfig = new LlmClientConfiguration() {
        @Override
        public String getProviderName() {
          return "invalid";
        }

        @Override
        public String getDefaultModel() {
          return "model";
        }

        @Override
        public Duration getConnectTimeout() {
          return Duration.ofSeconds(10);
        }
      };

      assertThatThrownBy(() -> new GeminiLlmClientFactory().create(mapper, invalidConfig))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("GeminiLlmClientFactory requires GeminiConfiguration");
    }
  }
}
