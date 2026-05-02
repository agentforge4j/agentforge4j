package com.agentforge4j.llm.claude;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnClaude() {
      ClaudeLlmClientFactory factory = new ClaudeLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("claude");
    }
  }

  @Nested
  class CreateTests {

    @Test
    void shouldCreateClaudeLlmClient() {
      ClaudeLlmClientFactory factory = new ClaudeLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();

      LlmClient client = factory.create(mapper, config);

      assertThat(client).isInstanceOf(ClaudeLlmClient.class);
      assertThat(client.getProviderName()).isEqualTo("claude");
    }

    @Test
    void shouldThrowWhenObjectMapperNull() {
      ClaudeLlmClientFactory factory = new ClaudeLlmClientFactory();
      ClaudeConfiguration config = FixedClaudeConfiguration.defaults();

      assertThatThrownBy(() -> factory.create(null, config))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void shouldThrowWhenConfigurationNull() {
      ClaudeLlmClientFactory factory = new ClaudeLlmClientFactory();
      ObjectMapper mapper = new ObjectMapper();

      assertThatThrownBy(() -> factory.create(mapper, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Claude configuration must not be null");
    }

    @Test
    void shouldThrowWhenConfigIsNotClaudeConfiguration() {
      ClaudeLlmClientFactory factory = new ClaudeLlmClientFactory();
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
          .hasMessageContaining("ClaudeLlmClientFactory requires ClaudeConfiguration");
    }
  }
}

