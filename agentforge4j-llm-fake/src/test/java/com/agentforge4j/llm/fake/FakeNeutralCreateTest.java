// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeNeutralCreateTest {

  @Test
  void buildsClientFromProgrammaticFakeConfiguration() {
    FakeResponseSource source = new RegistryFakeResponseSource(new FakeRunLifecycle());
    LlmClientFactoryContext context = new LlmClientFactoryContext(
        new ObjectMapper(), new FakeConfiguration(source), reference -> new LlmSecret("unused"));

    LlmClient client = new FakeLlmClientFactory().create(context);

    assertThat(client).isInstanceOf(FakeLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("fake");
  }

  @Test
  void failsWhenConfigurationIsNotFakeConfiguration() {
    LlmClientFactoryContext context = new LlmClientFactoryContext(
        new ObjectMapper(), genericNeutralConfig(), reference -> new LlmSecret("unused"));

    assertThatThrownBy(() -> new FakeLlmClientFactory().create(context))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("fake")
        .hasMessageContaining("response source");
  }

  private static LlmClientConfiguration genericNeutralConfig() {
    return new LlmClientConfiguration() {
      @Override
      public String getProviderName() {
        return "fake";
      }

      @Override
      public String getDefaultModel() {
        return "fake-model";
      }

      @Override
      public Duration getConnectTimeout() {
        return Duration.ZERO;
      }
    };
  }
}
