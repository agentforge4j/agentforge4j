// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Assembles the list of {@link com.agentforge4j.llm.api.LlmClient} instances from programmatic
 * {@link LlmProviderConfig} overrides and environment / system-property auto-discovery.
 *
 * <p>Internal — not part of the public API.
 */
final class LlmClientWiring {

  private static final System.Logger LOGGER =
      System.getLogger(LlmClientWiring.class.getName());

  private LlmClientWiring() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Assembles LLM clients from programmatic overrides and environment / system-property
   * auto-discovery. Providers are discovered dynamically via {@link ServiceLoader} — adding a new
   * provider module to the classpath is sufficient; no code changes required.
   *
   * <p>Precedence per provider (highest to lowest):
   * <ol>
   *   <li>Programmatic {@code withLlmProvider(LlmProviderConfig)} override.
   *   <li>System properties ({@code agentforge4j.llm.<provider>.*}).
   *   <li>Environment variables ({@code AGENTFORGE4J_LLM_<PROVIDER>_*}, normalised).
   * </ol>
   *
   * @param objectMapper          mapper used by provider factories
   * @param programmaticProviders providers set via {@code withLlmProvider}
   * @return list of configured clients; empty if none are available
   */
  static List<LlmClient> buildLlmClients(
      ObjectMapper objectMapper,
      Map<String, LlmProviderConfig> programmaticProviders) {

    Map<String, String> envConfig = ConfigReader.read();
    validateEnvConnectTimeouts(envConfig);
    List<LlmClientConfiguration> configurations = new ArrayList<>();

    ServiceLoader<LlmClientFactory> factories = ServiceLoader.load(LlmClientFactory.class);

    for (LlmClientFactory factory : factories) {
      LlmProviderConfig config = resolveProviderConfig(
          factory.getProviderName(), factory.requiresApiKey(), programmaticProviders, envConfig);
      if (config != null) {
        configurations.add(new ProviderConfigAdapter(config));
      }
    }

    if (configurations.isEmpty()) {
      return List.of();
    }

    try {
      DefaultLlmClientResolver resolver = DefaultLlmClientResolver.discover(objectMapper,
          configurations);
      return resolver.listAvailableClients().stream().map(resolver::resolve).toList();
    } catch (IllegalStateException exception) {
      LOGGER.log(System.Logger.Level.WARNING,
          "LLM client discovery failed; no providers will be available at runtime: {0}",
          exception.getMessage());
      return List.of();
    }
  }

  /**
   * Resolves the effective {@link LlmProviderConfig} for a provider, applying precedence rules:
   * programmatic > env/sys-prop > default (for no-api-key providers).
   *
   * @param provider              provider name from {@link LlmClientFactory#getProviderName()}
   * @param requiresApiKey        whether this provider requires an API key
   * @param programmaticProviders providers set via {@code withLlmProvider}
   * @param configMap             merged env/sys-prop map from {@link ConfigReader#read()}
   * @return config to use, or {@code null} if provider should be skipped
   */
  private static LlmProviderConfig resolveProviderConfig(
      String provider,
      boolean requiresApiKey,
      Map<String, LlmProviderConfig> programmaticProviders,
      Map<String, String> configMap) {

    // 1. Programmatic override wins entirely
    if (programmaticProviders.containsKey(provider)) {
      return programmaticProviders.get(provider);
    }

    // 2. Env / sys-prop config
    LlmProviderConfig config = buildLlmProviderConfigFromEnv(provider, requiresApiKey, configMap);
    if (config != null) {
      return config;
    }

    // 3. No-api-key providers get defaults when nothing is configured
    if (!requiresApiKey) {
      return buildDefaultConfig(provider);
    }

    return null;
  }

  private static void validateEnvConnectTimeouts(Map<String, String> envConfig) {
    String keyPrefix = "agentforge4j.llm.";
    String keySuffix = ".connect-timeout-seconds";
    for (Map.Entry<String, String> entry : envConfig.entrySet()) {
      String key = entry.getKey();
      if (!key.startsWith(keyPrefix) || !key.endsWith(keySuffix)) {
        continue;
      }
      String provider = key.substring(keyPrefix.length(), key.length() - keySuffix.length());
      parseConnectTimeoutSeconds(provider, entry.getValue());
    }
  }

  private static Duration parseConnectTimeoutSeconds(String provider, String timeoutStr) {
    if (timeoutStr == null) {
      return null;
    }
    try {
      return Duration.ofSeconds(Long.parseLong(timeoutStr));
    } catch (NumberFormatException exception) {
      throw new IllegalStateException(
          "Invalid value for agentforge4j.llm.%s.connect-timeout-seconds: '%s' — expected a whole number of seconds."
              .formatted(provider, timeoutStr),
          exception);
    }
  }

  private static LlmProviderConfig buildLlmProviderConfigFromEnv(
      String provider,
      boolean requiresApiKey,
      Map<String, String> configMap) {

    String prefix = "agentforge4j.llm." + provider + ".";
    String apiKey = configMap.get(prefix + "api-key");
    String baseUrl = configMap.get(prefix + "base-url");
    String defaultModel = configMap.get(prefix + "default-model");
    String timeoutStr = configMap.get(prefix + "connect-timeout-seconds");

    Duration connectTimeout = parseConnectTimeoutSeconds(provider, timeoutStr);

    boolean hasAnyConfig = isHasAnyConfig(apiKey, baseUrl, defaultModel, timeoutStr);

    if (requiresApiKey && apiKey == null) {
      return null;
    }
    if (!requiresApiKey && !hasAnyConfig) {
      // No env config — let resolveProviderConfig fall through to defaults
      return null;
    }

    return new LlmProviderConfig(provider, apiKey, baseUrl, defaultModel, connectTimeout);
  }

  private static boolean isHasAnyConfig(String apiKey, String baseUrl, String defaultModel,
      String timeoutStr) {
    return apiKey != null || baseUrl != null
        || defaultModel != null || timeoutStr != null;
  }

  /**
   * Builds a default {@link LlmProviderConfig} for providers that do not require an API key. Uses
   * the provider name only; callers supply their own defaults via {@link LlmProviderConfig}'s
   * per-provider builders when needed.
   *
   * @param provider provider name
   * @return minimal config with no credentials
   */
  private static LlmProviderConfig buildDefaultConfig(String provider) {
    return new LlmProviderConfig(provider, null, null, null, null);
  }
}
