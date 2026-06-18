// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-scoped support for {@link LlmClientFactory} captor stubs registered via
 * {@code META-INF/services}. Each captor records the {@link LlmClientFactoryContext} the wiring
 * passes it, so tests can assert the neutral configuration, resolved options, and credential the
 * bootstrap layer produced — without depending on real provider modules.
 */
final class CaptorLlmClientFactorySupport {

  static final Map<String, LlmClientFactoryContext> CAPTURED = new ConcurrentHashMap<>();

  private CaptorLlmClientFactorySupport() {
  }

  static void reset() {
    CAPTURED.clear();
  }

  /**
   * Base captor factory: records the neutral context and returns a stub client.
   */
  abstract static class Base implements LlmClientFactory {

    private final String provider;
    private final boolean requiresApiKey;

    Base(String provider, boolean requiresApiKey) {
      this.provider = provider;
      this.requiresApiKey = requiresApiKey;
    }

    @Override
    public final String getProviderName() {
      return provider;
    }

    @Override
    public final boolean requiresApiKey() {
      return requiresApiKey;
    }

    @Override
    public final LlmClient create(LlmClientFactoryContext context) {
      if (requiresApiKey) {
        // Resolve the credential as a real bearer factory would (fail-fast on an unresolvable ref).
        context.requireApiKey();
      }
      CAPTURED.put(getProviderName(), context);
      return new StubClient(getProviderName());
    }
  }

  private static final class StubClient implements LlmClient {

    private final String provider;

    StubClient(String provider) {
      this.provider = provider;
    }

    @Override
    public String getProviderName() {
      return provider;
    }

    @Override
    public LlmExecutionResponse execute(LlmExecutionRequest request) {
      throw new UnsupportedOperationException("captor stub is not invoked");
    }
  }
}
