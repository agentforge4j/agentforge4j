// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.vllm;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.starter.llmclient.NeutralOptions;
import java.time.Duration;

/**
 * Adapts {@link VllmLlmClientProperties} to the neutral {@link LlmClientConfiguration} SPI. vLLM requires no
 * credential, so no API-key reference is supplied; provider-specific settings are emitted as canonical dotted options
 * consumed by the provider's factory.
 */
record VllmConfigurationAdapter(VllmLlmClientProperties properties)
    implements LlmClientConfiguration {

  @Override
  public String getProviderName() {
    return "vllm";
  }

  @Override
  public String getDefaultModel() {
    return properties.defaultModel();
  }

  @Override
  public Duration getConnectTimeout() {
    return properties.connectTimeout();
  }

  @Override
  public String getBaseUrl() {
    return properties.url();
  }

  @Override
  public LlmProviderOptions getOptions() {
    return LlmProviderOptions.of("vllm", NeutralOptions.create()
        .duration("request.timeout", properties.requestTimeout())
        .toMap());
  }
}
