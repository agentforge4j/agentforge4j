// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import java.time.Duration;
import java.util.Optional;

/**
 * Settings used to construct an {@link LlmClient} for one provider: provider id, default model, and
 * HTTP connect timeout.
 */
public interface LlmClientConfiguration {

  /**
   * Provider id for this configuration (must align with the matching {@link LlmClientFactory}).
   *
   * @return non-blank provider id such as {@code "openai"} or {@code "ollama"}
   */
  String getProviderName();

  /**
   * Default model when a request does not set {@link LlmExecutionRequest#model()}.
   *
   * @return non-blank default model id for this provider
   */
  String getDefaultModel();

  /**
   * HTTP connect timeout for outbound requests to this provider.
   *
   * @return connect timeout duration
   */
  Duration getConnectTimeout();

  default Optional<LlmRetryPolicy> getRetryPolicy() {
    return Optional.empty();
  }
}
