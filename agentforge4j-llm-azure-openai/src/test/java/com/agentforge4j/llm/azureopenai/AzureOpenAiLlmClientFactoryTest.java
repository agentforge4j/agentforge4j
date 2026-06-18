// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.azureopenai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureOpenAiLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnAzureOpenai() {
      AzureOpenAiLlmClientFactory factory = new AzureOpenAiLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("azure-openai");
    }
  }
}
