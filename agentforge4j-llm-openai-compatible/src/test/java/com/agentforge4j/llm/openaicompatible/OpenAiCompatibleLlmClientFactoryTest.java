// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openaicompatible;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
  class CreateTests {

    @Test
    void shouldCreateOpenAiCompatibleLlmClient() {
      OpenAiCompatibleLlmClientFactory factory = new OpenAiCompatibleLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();
      OpenAiCompatibleConfiguration config = FixedOpenAiCompatibleConfiguration.defaults();

      LlmClient client = factory.create(mapper, config);

      assertThat(client).isInstanceOf(OpenAiCompatibleLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("openai-compatible");
    }

    @Test
    void shouldThrowWhenObjectMapperNull() {
      OpenAiCompatibleLlmClientFactory factory = new OpenAiCompatibleLlmClientFactory();
      OpenAiCompatibleConfiguration config = FixedOpenAiCompatibleConfiguration.defaults();

      assertThatThrownBy(() -> factory.create(null, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void shouldThrowWhenConfigurationNull() {
      OpenAiCompatibleLlmClientFactory factory = new OpenAiCompatibleLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> factory.create(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("OpenAiCompatible configuration must not be null");
    }

    @Test
    void shouldThrowWhenConfigIsNotOpenAiCompatibleConfiguration() {
      OpenAiCompatibleLlmClientFactory factory = new OpenAiCompatibleLlmClientFactory();
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
          .hasMessageContaining(
              "OpenAiCompatibleLlmClientFactory requires OpenAiCompatibleConfiguration");
    }
  }
}
