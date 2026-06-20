// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.support;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only {@link LlmClientFactory} for the {@code looptest} provider (a non-hyphenated id so it
 * survives env-variable normalisation). It requires no API key and captures the resolved
 * {@code baseUrl} it is handed, so a test can assert which configuration layer won
 * (programmatic &gt; system-property &gt; env). Discovered via {@code ServiceLoader} from a
 * test-scope {@code META-INF/services} registration; it is constructed only when the {@code looptest}
 * provider is actually configured, so other verification tests are unaffected.
 */
public final class CapturingLlmClientFactory implements LlmClientFactory {

  /** Base URLs captured across {@link #create} calls; cleared by the test before each build. */
  public static final List<String> CAPTURED_BASE_URLS = new CopyOnWriteArrayList<>();

  @Override
  public String getProviderName() {
    return "looptest";
  }

  @Override
  public boolean requiresApiKey() {
    return false;
  }

  @Override
  public LlmClient create(LlmClientFactoryContext context) {
    CAPTURED_BASE_URLS.add(context.configuration().getBaseUrl());
    return new CaptureOnlyClient();
  }

  private static final class CaptureOnlyClient implements LlmClient {

    @Override
    public String getProviderName() {
      return "looptest";
    }

    @Override
    public LlmExecutionResponse execute(LlmExecutionRequest request) {
      throw new UnsupportedOperationException("capture-only test client is never executed");
    }
  }
}
