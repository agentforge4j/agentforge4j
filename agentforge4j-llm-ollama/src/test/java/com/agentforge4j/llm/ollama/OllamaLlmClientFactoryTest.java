package com.agentforge4j.llm.ollama;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaLlmClientFactoryTest {

  static class TestOllamaConfiguration implements OllamaConfiguration {

    @Override
    public String getDefaultModel() {
      return "llama2";
    }

    @Override
    public Duration getConnectTimeout() {
      return Duration.ofSeconds(10);
    }

    @Override
    public Duration getRequestTimeout() {
      return Duration.ofSeconds(30);
    }

    @Override
    public String getUrl() {
      return "http://localhost:11434/api/chat";
    }
  }

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnOllama() {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("ollama");
    }
  }

  @Nested
  class CreateTests {

    @Test
    void shouldCreateOllamaLlmClient() {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();
      OllamaConfiguration config = new TestOllamaConfiguration();

      LlmClient client = factory.create(mapper, config);

      assertThat(client).isInstanceOf(OllamaLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("ollama");
    }

    @Test
    void shouldThrowWhenConfigIsNotOllamaConfiguration() {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
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

      assertThatThrownBy(() -> factory.create(mapper, invalidConfig))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OllamaLlmClientFactory requires OllamaConfiguration");
    }

    @Test
    void shouldThrowWhenConfigurationNull() {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> factory.create(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Ollama configuration must not be null");
    }

    @Test
    void shouldThrowWhenObjectMapperNull() {
      OllamaLlmClientFactory factory = new OllamaLlmClientFactory();
      OllamaConfiguration config = new TestOllamaConfiguration();

      assertThatThrownBy(() -> factory.create(null, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}

