// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.claude;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void shouldReturnClaude() {
      ClaudeLlmClientFactory factory = new ClaudeLlmClientFactory();
      assertThat(factory.getProviderName()).isEqualTo("claude");
    }
  }
}

