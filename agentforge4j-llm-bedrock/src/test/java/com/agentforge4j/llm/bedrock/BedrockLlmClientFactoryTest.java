// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.llm.LlmClientFactory;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class BedrockLlmClientFactoryTest {

  private final BedrockLlmClientFactory factory = new BedrockLlmClientFactory();

  @Test
  void providerNameIsBedrock() {
    assertThat(factory.getProviderName()).isEqualTo("bedrock");
  }

  @Test
  void serviceLoaderFindsBedrockFactory() {
    assertThat(ServiceLoader.load(LlmClientFactory.class).stream()
        .map(ServiceLoader.Provider::get)
        .map(LlmClientFactory::getProviderName))
        .contains("bedrock");
  }

  @Test
  void metaInfServicesRegistersBedrockFactory() throws Exception {
    String resource = "META-INF/services/com.agentforge4j.llm.LlmClientFactory";
    try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      assertThat(in).as(resource).isNotNull();
      String text = new String(in.readAllBytes(), UTF_8).strip();
      assertThat(text).isEqualTo(BedrockLlmClientFactory.class.getName());
    }
  }
}
