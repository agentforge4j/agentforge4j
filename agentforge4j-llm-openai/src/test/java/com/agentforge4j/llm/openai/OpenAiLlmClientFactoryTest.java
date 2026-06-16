// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnOpenai() {
      OpenAiLlmClientFactory factory = new OpenAiLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("openai");
    }
  }

  @Nested
  class CreateTests {

    @Test
    void shouldCreateOpenAiLlmClient() {
      OpenAiLlmClientFactory factory = new OpenAiLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();
      OpenAiConfiguration config = FixedOpenAiConfiguration.defaults();

      LlmClient client = factory.create(mapper, config);

      assertThat(client).isInstanceOf(OpenAiLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("openai");
    }

    @Test
    void shouldThrowWhenConfigIsNotOpenAiConfiguration() {
      OpenAiLlmClientFactory factory = new OpenAiLlmClientFactory();
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
          .hasMessageContaining("OpenAiLlmClientFactory requires OpenAiConfiguration");
    }
  }
}


