// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.RetryingLlmClientResolver;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LlmRetryPolicyWiringTest {

  private final Map<String, String> originalValues = new HashMap<>();

  @Mock
  private LlmClientResolver mockResolver;

  @AfterEach
  void restoreProperties() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      String key = entry.getKey();
      String previous = entry.getValue();
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
    originalValues.clear();
  }

  @Test
  void retryPolicyWithMaxAttemptsAboveOneWrapsResolver() {
    LlmRetryPolicy policy = new LlmRetryPolicy(3, 200, 10_000, 30_000);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmRetryPolicy(policy)
        .build();

    assertThat(af.components().llmClientResolver()).isInstanceOf(RetryingLlmClientResolver.class);
  }

  @Test
  void retryPolicyWithMaxAttemptsOfOneDoesNotWrap() {
    LlmRetryPolicy policy = new LlmRetryPolicy(1, 200, 10_000, 30_000);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmRetryPolicy(policy)
        .build();

    assertThat(af.components().llmClientResolver()).isNotInstanceOf(RetryingLlmClientResolver.class);
  }

  @Test
  void maxAttemptsOfZeroIsRejectedByPolicy() {
    assertThatThrownBy(() -> new LlmRetryPolicy(0, 200, 10_000, 30_000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void explicitResolverNotWrappedEvenWithRetryPolicy() {
    LlmRetryPolicy policy = new LlmRetryPolicy(3, 200, 10_000, 30_000);

    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(mockResolver)
        .withLlmRetryPolicy(policy)
        .build();

    assertThat(af.components().llmClientResolver()).isSameAs(mockResolver);
    assertThat(af.components().llmClientResolver()).isNotInstanceOf(RetryingLlmClientResolver.class);
  }

  @Test
  void nullRetryPolicyThrows() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults().withLlmRetryPolicy(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
