package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAiLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnAzureOpenai() {
      AzureOpenAiLlmClientFactory factory = new AzureOpenAiLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("azure-openai");
    }
  }

  @Nested
  class CreateTests {

    @Test
    void shouldCreateAzureOpenAiLlmClient() {
      AzureOpenAiLlmClientFactory factory = new AzureOpenAiLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();

      LlmClient client = factory.create(mapper, FixedAzureOpenAiConfiguration.defaults());

      assertThat(client).isInstanceOf(AzureOpenAiLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("azure-openai");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      AzureOpenAiLlmClientFactory factory = new AzureOpenAiLlmClientFactory();

      assertThatThrownBy(() -> factory.create(null, FixedAzureOpenAiConfiguration.defaults()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void should_throw_when_configuration_null() {
      AzureOpenAiLlmClientFactory factory = new AzureOpenAiLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> factory.create(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("LLM client configuration must not be null");
    }

    @Test
    void shouldThrowWhenConfigIsNotAzureOpenAiConfiguration() {
      AzureOpenAiLlmClientFactory factory = new AzureOpenAiLlmClientFactory();
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
          .hasMessageContaining("AzureOpenAiLlmClientFactory requires AzureOpenAiConfiguration");
    }
  }
}
