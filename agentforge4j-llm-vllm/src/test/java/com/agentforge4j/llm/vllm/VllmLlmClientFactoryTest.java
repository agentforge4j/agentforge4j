// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.vllm;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VllmLlmClientFactoryTest {

  @Nested
  class GetProviderNameTests {

    @Test
    void should_return_vllm() {
      assertThat(new VllmLlmClientFactory().getProviderName()).isEqualTo("vllm");
    }
  }
}
