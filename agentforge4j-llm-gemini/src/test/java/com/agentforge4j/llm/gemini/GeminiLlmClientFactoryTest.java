// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnGemini() {
      assertThat(new GeminiLlmClientFactory().getProviderName()).isEqualTo("gemini");
    }
  }
}
