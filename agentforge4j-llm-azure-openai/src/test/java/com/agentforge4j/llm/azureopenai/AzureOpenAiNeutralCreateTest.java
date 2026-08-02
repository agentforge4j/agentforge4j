// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureOpenAiNeutralCreateTest {

  private static final LlmSecretResolver RESOLVER = reference -> new LlmSecret(reference.literalValue());

  @Test
  void buildsClientFromNeutralContext() {
    LlmClient client = new AzureOpenAiLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(true), RESOLVER));

    assertThat(client).isInstanceOf(AzureOpenAiLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("azure-openai");
  }

  @Test
  void failsWhenDeploymentMissing() {
    assertThatThrownBy(() -> new AzureOpenAiLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(false), RESOLVER)))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("deployment");
  }

  @Test
  void defaultsRequestTimeoutToTwoMinutesWhenAbsent() {
    AzureOpenAiNeutralConfiguration config = AzureOpenAiNeutralConfiguration.fromNeutral(
        neutralWithoutRequestTimeout(), new LlmSecret("az-test"));

    assertThat(config.getRequestTimeout()).isEqualTo(AzureOpenAiDefaults.REQUEST_TIMEOUT);
  }

  private static LlmClientConfiguration neutral(boolean withDeployment) {
    Map<String, String> options = new HashMap<>();
    options.put("api.version", "2024-02-01");
    options.put("request.timeout", "PT30S");
    if (withDeployment) {
      options.put("deployment", "gpt-4o-deployment");
    }
    return config(options);
  }

  private static LlmClientConfiguration neutralWithoutRequestTimeout() {
    Map<String, String> options = new HashMap<>();
    options.put("api.version", "2024-02-01");
    options.put("deployment", "gpt-4o-deployment");
    return config(options);
  }

  private static LlmClientConfiguration config(Map<String, String> options) {
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "azure-openai";
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
        return "https://myresource.openai.azure.com";
      }

      @Override
      public Optional<LlmSecretReference> getApiKeyReference() {
        return Optional.of(LlmSecretReference.literal("az-test"));
      }

      @Override
      public LlmProviderOptions getOptions() {
        return LlmProviderOptions.of("azure-openai", options);
      }
    };
  }
}
