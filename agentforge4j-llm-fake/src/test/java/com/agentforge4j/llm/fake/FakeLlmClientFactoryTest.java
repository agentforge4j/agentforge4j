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

class FakeLlmClientFactoryTest {

  private final FakeLlmClientFactory factory = new FakeLlmClientFactory();

  @Test
  void providerName_isFake_andNoApiKeyRequired() {
    assertThat(factory.getProviderName()).isEqualTo("fake");
    assertThat(factory.requiresApiKey()).isFalse();
  }

  @Test
  void create_withFakeConfiguration_buildsFakeClientBoundToTheSource() {
    FakeResponseSource source = new RegistryFakeResponseSource(new FakeRunLifecycle());
    LlmClientFactoryContext context = new LlmClientFactoryContext(
        new ObjectMapper(), new FakeConfiguration(source), reference -> new LlmSecret("unused"));
    LlmClient client = factory.create(context);

    assertThat(client).isInstanceOf(FakeLlmClient.class);
    assertThat(client.getProviderName()).isEqualTo("fake");
  }

  @Test
  void create_withNonFakeConfiguration_throwsActionableError() {
    LlmClientConfiguration generic = new LlmClientConfiguration() {
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

    LlmClientFactoryContext context = new LlmClientFactoryContext(
        new ObjectMapper(), generic, reference -> new LlmSecret("unused"));

    assertThatThrownBy(() -> factory.create(context))
        .isInstanceOf(LlmProviderConfigurationException.class)
        .hasMessageContaining("FakeConfiguration")
        .hasMessageContaining("withLlmProvider");
  }
}
