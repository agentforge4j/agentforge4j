// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientFactoryContextTest {

  private static final LlmSecretResolver RESOLVER = reference -> new LlmSecret("resolved");

  @Test
  void rejects_null_components() {
    ObjectMapper mapper = new ObjectMapper();
    LlmClientConfiguration config = new StubConfiguration();

    assertThatThrownBy(() -> new LlmClientFactoryContext(null, config, RESOLVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LlmClientFactoryContext(mapper, null, RESOLVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LlmClientFactoryContext(mapper, config, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class StubConfiguration implements LlmClientConfiguration {

    @Override
    public String getProviderName() {
      return "stub";
    }

    @Override
    public String getDefaultModel() {
      return "stub-model";
    }

    @Override
    public Duration getConnectTimeout() {
      return Duration.ofSeconds(5);
    }
  }
}
