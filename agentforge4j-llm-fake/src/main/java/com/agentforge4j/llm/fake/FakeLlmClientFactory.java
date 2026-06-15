package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link LlmClientFactory} for the fake provider, discovered via {@link java.util.ServiceLoader}. Requires a
 * {@link FakeConfiguration} (carrying the shared {@link FakeResponseSource}); any other configuration type raises an
 * {@link IllegalArgumentException}, the same contract Ollama uses for its configuration subtype.
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

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    Validate.notNull(config, "Fake configuration must not be null");
    if (!(config instanceof FakeConfiguration fakeConfig)) {
      throw new IllegalArgumentException(
          ("FakeLlmClientFactory requires a FakeConfiguration carrying a FakeResponseSource but got: "
              + "%s. Wire the fake provider via the Spring Boot starter or programmatic "
              + "withLlmProvider(...).").formatted(config.getClass().getName()));
    }
    return new FakeLlmClient(fakeConfig.responseSource());
  }
}
