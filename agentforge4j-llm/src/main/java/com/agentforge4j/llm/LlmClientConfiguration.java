// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmRetryPolicy;
import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.util.Optional;

/**
 * Settings used to construct an {@link LlmClient} for one provider: provider id, default model, and HTTP connect
 * timeout.
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

  /**
   * The retry policy this configuration requests, or {@code null} when none is configured —
   * mirroring {@link com.agentforge4j.llm.api.LlmClient#getRetryPolicy()}'s nullable contract so
   * both retry-policy surfaces represent absence the same way. Callers that need a policy either
   * way fall back to {@link LlmRetryPolicy#defaults()} on a {@code null} return.
   *
   * @return the configured retry policy, or {@code null} when this configuration has none
   */
  default LlmRetryPolicy getRetryPolicy() {
    return null;
  }

  /**
   * Service base URL for HTTP providers (scheme + host, optional port), without trailing slash.
   *
   * <p>Returns a nullable {@code String} to match the existing provider configurations that already
   * declare {@code String getBaseUrl()}; {@code null} means the provider has a fixed endpoint or none applies.
   *
   * @return the base URL, or {@code null} when not applicable
   */
  default String getBaseUrl() {
    return null;
  }

  /**
   * The provider credential, carried as a reference so a raw value never travels through the wiring layer. A factory
   * resolves it via {@link LlmClientFactoryContext#secretResolver()}.
   *
   * @return the credential reference, or empty for providers that need none
   */
  default Optional<LlmSecretReference> getApiKeyReference() {
    return Optional.empty();
  }

  /**
   * Validated, provider-specific options beyond the common settings above (see {@link LlmProviderOptions}).
   *
   * @return the provider options; never {@code null} (empty when none)
   */
  default LlmProviderOptions getOptions() {
    return LlmProviderOptions.empty();
  }

  /**
   * Returns the required base URL, for providers that need one.
   *
   * @return the non-blank base URL
   *
   * @throws LlmProviderConfigurationException if no base URL is configured (secret-safe message naming the provider)
   */
  default String requireBaseUrl() {
    return Validate.notBlank(getBaseUrl(), () -> new LlmProviderConfigurationException(
        "Provider '%s' requires a base URL but none is configured".formatted(getProviderName())));
  }
}
