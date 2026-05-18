package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test {@link LlmClientFactory} registered for {@link java.util.ServiceLoader} coverage. Must have
 * a public no-arg constructor for SPI.
 */
public final class ServiceLoaderStubLlmClientFactory implements LlmClientFactory {

  /**
   * Provider name used by {@link DefaultLlmClientResolverDiscoverIT}.
   */
  public static final String PROVIDER = "discover-stub";

  public ServiceLoaderStubLlmClientFactory() {
  }

  @Override
  public String getProviderName() {
    return PROVIDER;
  }

  @Override
  public LlmClient create(ObjectMapper objectMapper, LlmClientConfiguration config) {
    return new LlmClient() {
      @Override
      public String getProviderName() {
        return config.getProviderName();
      }

      @Override
      public String execute(LlmExecutionRequest request) {
        return "stub:" + request.userInput();
      }
    };
  }
}
