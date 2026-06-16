// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VllmLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void should_return_vllm() {
      assertThat(new VllmLlmClientFactory().getProviderName()).isEqualTo("vllm");
    }
  }

  @Nested
  class CreateTests {

    @Test
    void should_create_vllm_llm_client() {
      VllmLlmClientFactory factory = new VllmLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();
      VllmConfiguration config = FixedVllmConfiguration.defaults();

      LlmClient client = factory.create(mapper, config);

      assertThat(client).isInstanceOf(VllmLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("vllm");
    }

    @Test
    void should_throw_when_config_is_not_vllm_configuration() {
      VllmLlmClientFactory factory = new VllmLlmClientFactory();
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
          .hasMessageContaining("VllmLlmClientFactory requires VllmConfiguration");
    }

    @Test
    void should_throw_when_configuration_null() {
      VllmLlmClientFactory factory = new VllmLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> factory.create(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Vllm configuration must not be null");
    }

    @Test
    void should_throw_when_object_mapper_null() {
      VllmLlmClientFactory factory = new VllmLlmClientFactory();
      VllmConfiguration config = FixedVllmConfiguration.defaults();

      assertThatThrownBy(() -> factory.create(null, config))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
