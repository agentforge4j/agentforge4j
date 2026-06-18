// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VllmNeutralCreateTest {

  @Test
  void buildsClientFromNeutralContext() {
    LlmClient client = new VllmLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(true), reference -> new LlmSecret("unused")));

    assertThat(client).isInstanceOf(VllmLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("vllm");
  }

  @Test
  void failsWhenBaseUrlMissing() {
    assertThatThrownBy(() -> new VllmLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(false), reference -> new LlmSecret("unused"))))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("vllm")
        .hasMessageContaining("base URL");
  }

  private static LlmClientConfiguration neutral(boolean withBaseUrl) {
    Map<String, String> options = new HashMap<>();
    options.put("request.timeout", "PT30S");
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "vllm";
      }

      @Override
      public String getDefaultModel() {
        return "meta-llama/Llama-3-8b";
      }

      @Override
      public Duration getConnectTimeout() {
        return Duration.ofSeconds(10);
      }

      @Override
      public String getBaseUrl() {
        return withBaseUrl ? "http://localhost:8000/v1/chat/completions" : null;
      }

      @Override
      public LlmProviderOptions getOptions() {
        return LlmProviderOptions.of("vllm", options);
      }
    };
  }
}
