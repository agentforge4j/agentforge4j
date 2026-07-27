// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedrockNeutralCreateTest {

  @Test
  void buildsClientFromNeutralContextWithoutCredentialReference() {
    LlmClient client = new BedrockLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(true), reference -> new LlmSecret("unused")));

    assertThat(client).isInstanceOf(BedrockLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("bedrock");
  }

  @Test
  void mapsNeutralConfigToProductionDefaults() {
    BedrockNeutralConfiguration config = BedrockNeutralConfiguration.fromNeutral(neutral(true));

    assertThat(config.getRegion()).isEqualTo("eu-west-1");
    assertThat(config.getAnthropicVersion()).isEqualTo("bedrock-2023-05-31");
    // Production path: no endpoint or credential override → AWS default chain + regional endpoint.
    assertThat(config.getEndpointOverride()).isNull();
    assertThat(config.getCredentialsProvider()).isNull();
    // Optional tuning absent → null (model defaults apply).
    assertThat(config.getMaxTokens()).isNull();
    assertThat(config.getTemperature()).isNull();
  }

  @Test
  void failsWhenRegionMissing() {
    assertThatThrownBy(() -> new BedrockLlmClientFactory().create(
        new LlmClientFactoryContext(new ObjectMapper(), neutral(false), reference -> new LlmSecret("unused"))))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("region");
  }

  @Test
  void defaultsRequestTimeoutToTwoMinutesWhenAbsent() {
    BedrockNeutralConfiguration config = BedrockNeutralConfiguration.fromNeutral(neutralWithoutRequestTimeout());

    assertThat(config.getRequestTimeout()).isEqualTo(BedrockDefaults.REQUEST_TIMEOUT);
  }

  private static LlmClientConfiguration neutral(boolean withRegion) {
    Map<String, String> options = new HashMap<>();
    options.put("anthropic.version", "bedrock-2023-05-31");
    options.put("request.timeout", "PT60S");
    if (withRegion) {
      options.put("region", "eu-west-1");
    }
    return config(options);
  }

  private static LlmClientConfiguration neutralWithoutRequestTimeout() {
    Map<String, String> options = new HashMap<>();
    options.put("anthropic.version", "bedrock-2023-05-31");
    options.put("region", "eu-west-1");
    return config(options);
  }

  private static LlmClientConfiguration config(Map<String, String> options) {
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "bedrock";
      }

      @Override
      public String getDefaultModel() {
        return "anthropic.claude-3-5-sonnet";
      }

      @Override
      public Duration getConnectTimeout() {
        return Duration.ofSeconds(10);
      }

      @Override
      public LlmProviderOptions getOptions() {
        return LlmProviderOptions.of("bedrock", options);
      }
    };
  }
}
