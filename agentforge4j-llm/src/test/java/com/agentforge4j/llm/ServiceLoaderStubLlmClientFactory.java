// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;

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
  public LlmClient create(LlmClientFactoryContext context) {
    return new LlmClient() {
      @Override
      public String getProviderName() {
        return context.configuration().getProviderName();
      }

      @Override
      public LlmExecutionResponse execute(LlmExecutionRequest request) {
        return new LlmExecutionResponse("stub:" + request.userInput(), null, null);
      }
    };
  }
}
