// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnOpenai() {
      OpenAiLlmClientFactory factory = new OpenAiLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("openai");
    }
  }
}


