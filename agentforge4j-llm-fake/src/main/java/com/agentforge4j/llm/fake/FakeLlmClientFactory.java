// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;

/**
 * {@link LlmClientFactory} for the fake provider, discovered via {@link java.util.ServiceLoader}. Requires a
 * {@link FakeConfiguration} (carrying the shared {@link FakeResponseSource}); any other configuration type raises an
 * {@link LlmProviderConfigurationException}.
 */
public final class FakeLlmClientFactory implements LlmClientFactory {

  private static final String PROVIDER_NAME = "fake";

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  /**
   * Creates a fake client from a {@link LlmClientFactoryContext}. The fake provider's load-bearing input is a
   * {@link FakeResponseSource}, which cannot be expressed as neutral string options, so it must be supplied
   * programmatically as a {@link FakeConfiguration} (via {@code withLlmProvider}) — the neutral env/system-property
   * path cannot configure it.
   *
   * @param context the factory inputs
   *
   * @return a new fake LLM client
   *
   * @throws LlmProviderConfigurationException if the configuration is not a {@link FakeConfiguration}
   */
  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    Validate.notNull(context, "context must not be null");
    LlmClientConfiguration config = context.configuration();
    if (!(config instanceof FakeConfiguration fakeConfig)) {
      throw new LlmProviderConfigurationException(
          "Provider 'fake' requires a FakeConfiguration carrying a response source; it cannot be "
              + "configured from neutral options. Wire it via programmatic withLlmProvider(...).");
    }
    return new FakeLlmClient(fakeConfig.responseSource());
  }
}
