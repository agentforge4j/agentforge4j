// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Configuration for the fake provider. Unlike HTTP providers, the load-bearing field is the {@link FakeResponseSource}
 * — the shared per-run store that the registration API (test runner / demo flow) also holds — so the
 * {@link FakeLlmClientFactory}-built client and the registration API resolve against one store.
 *
 * <p>Wire this through the embedding application or programmatic {@code withLlmProvider(...)}; the
 * env/system-property bootstrap discovery path supplies a generic configuration and cannot carry a source, so it does
 * not configure the fake provider (the same way Ollama requires its own configuration subtype).
 */
public final class FakeConfiguration implements LlmClientConfiguration {

  private static final String PROVIDER_NAME = "fake";
  private static final String DEFAULT_MODEL = "fake-model";

  private final FakeResponseSource responseSource;
  private final String defaultModel;
  private final Duration connectTimeout;

  /**
   * Creates a configuration with the default model id and a zero connect timeout.
   *
   * @param responseSource the scripted response source; must not be {@code null}
   */
  public FakeConfiguration(FakeResponseSource responseSource) {
    this(responseSource, DEFAULT_MODEL, Duration.ZERO);
  }

  /**
   * Creates a configuration with an explicit default model and connect timeout.
   *
   * @param responseSource the scripted response source; must not be {@code null}
   * @param defaultModel   default model id reported to callers; must not be blank
   * @param connectTimeout nominal connect timeout (unused by the fake transport); must not be {@code null}
   */
  public FakeConfiguration(FakeResponseSource responseSource, String defaultModel,
      Duration connectTimeout) {
    this.responseSource = Validate.notNull(responseSource, "responseSource must not be null");
    this.defaultModel = Validate.notBlank(defaultModel, "defaultModel must not be blank");
    this.connectTimeout = Validate.notNull(connectTimeout, "connectTimeout must not be null");
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public String getDefaultModel() {
    return defaultModel;
  }

  @Override
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * Returns the shared scripted response source the client resolves against.
   *
   * @return the response source; never {@code null}
   */
  public FakeResponseSource responseSource() {
    return responseSource;
  }
}
