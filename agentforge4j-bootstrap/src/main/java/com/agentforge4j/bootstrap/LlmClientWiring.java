// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.LlmSecretResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.agentforge4j.util.time.DurationParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;

/**
 * Assembles {@link LlmClient} instances from programmatic {@link LlmProviderConfig} overrides and environment /
 * system-property auto-discovery, passing each provider factory a neutral {@link LlmClientFactoryContext}.
 *
 * <h2>Configuration keys</h2>
 * Auto-discovery reads keys under {@code agentforge4j.llm.<provider>.} in the canonical
 * <b>lowercase, dot-separated</b> form: {@code api.key} (credential — a literal value or an
 * {@code ${env:NAME}}/{@code ${sysprop:name}} reference), {@code base.url}, {@code default.model},
 * {@code connect.timeout} (ISO-8601 like {@code PT30S}, or the shared compact shorthand like {@code 30s} /
 * {@code 500ms} — see {@link DurationParser}). Every other {@code agentforge4j.llm.<provider>.*} key is a
 * provider-specific option (for example {@code request.timeout}, {@code auth.header.name}, {@code api.version}).
 *
 * <p>Environment variables map by {@code AGENTFORGE4J_LLM_<PROVIDER>_<KEY>} with {@code _} normalised
 * to {@code .} (see {@code ConfigReader}). That normalisation also collapses the separator inside a hyphenated provider
 * id, so <b>hyphenated provider ids ({@code azure-openai}, {@code openai-compatible}) cannot be configured via
 * environment variables</b> — use system properties (which preserve the hyphen) or programmatic
 * {@code withLlmProvider(...)}. Disambiguating hyphenated provider names from environment variables needs
 * provider-boundary normalization, tracked under issue #99.
 *
 * <p>Precedence per provider, highest to lowest: programmatic {@code withLlmProvider} &gt; system
 * property &gt; environment variable.
 *
 * <p>Internal — not part of the public API.
 */
final class LlmClientWiring {

  private static final String KEY_PREFIX = "agentforge4j.llm.";
  private static final String API_KEY = "api.key";
  private static final String BASE_URL = "base.url";
  private static final String DEFAULT_MODEL = "default.model";
  private static final String CONNECT_TIMEOUT = "connect.timeout";
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

  private LlmClientWiring() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Assembles LLM clients. Fails fast with {@link LlmProviderConfigurationException} on a configured provider with no
   * factory, a duplicate factory contributor, or an invalid/unresolvable provider configuration. A factory present
   * without configuration is simply not constructed; an empty result is valid.
   *
   * @param objectMapper          mapper used by provider factories
   * @param programmaticProviders providers set via {@code withLlmProvider}
   * @param secretResolver        resolver for credential references
   *
   * @return list of configured clients; possibly empty
   */
  static List<LlmClient> buildLlmClients(
      ObjectMapper objectMapper,
      Map<String, LlmProviderConfig> programmaticProviders,
      LlmSecretResolver secretResolver) {
    return assembleClients(objectMapper, programmaticProviders, secretResolver, discoverFactories());
  }

  /**
   * Assembly core, separated from {@link ServiceLoader} discovery so it can be driven with an explicit factory list in
   * tests. Detects duplicate contributors, fails fast on a configured programmatic provider with no factory, and builds
   * each configured provider via {@code create(context)}.
   */
  static List<LlmClient> assembleClients(
      ObjectMapper objectMapper,
      Map<String, LlmProviderConfig> programmaticProviders,
      LlmSecretResolver secretResolver,
      List<LlmClientFactory> discoveredFactories) {
    Validate.notNull(objectMapper, "objectMapper must not be null");
    Validate.notNull(programmaticProviders, "programmaticProviders must not be null");
    Validate.notNull(secretResolver, "secretResolver must not be null");
    Validate.notNull(discoveredFactories, "discoveredFactories must not be null");

    Map<String, String> envConfig = ConfigReader.read();
    Map<String, LlmProviderConfig> programmaticByProvider = normalizeProgrammatic(programmaticProviders);
    Map<String, LlmClientFactory> factoriesByProvider = indexFactories(discoveredFactories);

    validateProviders(programmaticByProvider, factoriesByProvider, envConfig);

    List<LlmClient> clients = resolveClients(objectMapper, secretResolver,
        factoriesByProvider, programmaticByProvider, envConfig);
    return List.copyOf(clients);
  }

  private static List<LlmClient> resolveClients(ObjectMapper objectMapper, LlmSecretResolver secretResolver,
      Map<String, LlmClientFactory> factoriesByProvider, Map<String, LlmProviderConfig> programmaticByProvider,
      Map<String, String> envConfig) {
    List<LlmClient> clients = new ArrayList<>();
    for (LlmClientFactory factory : factoriesByProvider.values()) {
      LlmProviderConfig config = resolveProviderConfig(
          factory.getProviderName(), factory.requiresApiKey(), programmaticByProvider, envConfig);
      if (config != null) {
        LlmClientFactoryContext context = new LlmClientFactoryContext(
            objectMapper, new NeutralLlmClientConfiguration(config), secretResolver);
        clients.add(factory.create(context));
      }
    }
    return clients;
  }

  private static void validateProviders(Map<String, LlmProviderConfig> programmaticByProvider,
      Map<String, LlmClientFactory> factoriesByProvider, Map<String, String> envConfig) {
    for (String provider : programmaticByProvider.keySet()) {
      Validate.isTrue(factoriesByProvider.containsKey(provider), () -> new LlmProviderConfigurationException(
          "No LLM provider factory found for configured provider '%s'. Available providers: %s"
              .formatted(provider, factoriesByProvider.keySet())));
    }
    failOnUnknownEnvProviders(envConfig, factoriesByProvider.keySet());
  }

  private static Map<String, LlmProviderConfig> normalizeProgrammatic(
      Map<String, LlmProviderConfig> programmaticProviders) {
    Map<String, LlmProviderConfig> byProvider = new LinkedHashMap<>();
    for (LlmProviderConfig config : programmaticProviders.values()) {
      byProvider.put(normalize(config.provider()), config);
    }
    return byProvider;
  }

  private static List<LlmClientFactory> discoverFactories() {
    List<LlmClientFactory> discovered = new ArrayList<>();
    ServiceLoader.load(LlmClientFactory.class, Thread.currentThread().getContextClassLoader()).forEach(discovered::add);
    return discovered;
  }

  private static Map<String, LlmClientFactory> indexFactories(List<LlmClientFactory> factories) {
    Map<String, LlmClientFactory> byProvider = new LinkedHashMap<>();
    for (LlmClientFactory factory : factories) {
      String provider = normalize(factory.getProviderName());
      LlmClientFactory existing = byProvider.putIfAbsent(provider, factory);
      Validate.isTrue(existing == null, () -> new LlmProviderConfigurationException(
          "Duplicate LLM provider factory for '%s': %s and %s".formatted(
              provider, existing.getClass().getName(), factory.getClass().getName())));
    }
    return byProvider;
  }

  private static LlmProviderConfig resolveProviderConfig(
      String provider,
      boolean requiresApiKey,
      Map<String, LlmProviderConfig> programmaticByProvider,
      Map<String, String> configMap) {
    LlmProviderConfig programmatic = programmaticByProvider.get(normalize(provider));
    if (programmatic != null) {
      return programmatic;
    }
    return buildFromEnv(provider, requiresApiKey, configMap);
  }

  private static LlmProviderConfig buildFromEnv(
      String provider,
      boolean requiresApiKey,
      Map<String, String> configMap) {
    String prefix = KEY_PREFIX + provider + ".";
    String apiKey = configMap.get(prefix + API_KEY);
    String baseUrl = configMap.get(prefix + BASE_URL);
    String defaultModel = configMap.get(prefix + DEFAULT_MODEL);
    String connectTimeoutValue = configMap.get(prefix + CONNECT_TIMEOUT);
    Map<String, String> options = collectOptions(prefix, configMap);

    boolean hasAnyConfig = apiKey != null || baseUrl != null || defaultModel != null
        || connectTimeoutValue != null || !options.isEmpty();
    if (!hasAnyConfig) {
      // Provider not configured at this layer — not an error; simply not constructed.
      return null;
    }
    if (requiresApiKey && apiKey == null) {
      // Explicitly configured but missing its required credential — fail fast, do not skip.
      String apiKeyKey = KEY_PREFIX + provider + "." + API_KEY;
      throw new LlmProviderConfigurationException(
          "Provider '%s' is configured but is missing the required API key '%s'"
              .formatted(provider, apiKeyKey));
    }

    LlmSecretReference apiKeyReference = apiKey == null ? null : LlmSecretReference.parse(apiKey);
    Duration connectTimeout = parseConnectTimeout(provider, connectTimeoutValue);
    return new LlmProviderConfig(provider, apiKeyReference, baseUrl, defaultModel, connectTimeout,
        options);
  }

  /**
   * Fails fast when the configuration names an LLM provider that has no discovered factory. A provider is recognised by
   * a definitive credential/endpoint key ({@code .api.key} / {@code .base.url}) under
   * {@code agentforge4j.llm.<provider>.} — this deliberately ignores non-provider namespaces such as
   * {@code agentforge4j.llm.cache.*} and {@code agentforge4j.llm.model-tiers.*}. A collapsed hyphenated provider (e.g.
   * {@code azure-openai} arriving from an env var as {@code azure.openai}) therefore surfaces here as an unknown
   * provider rather than being silently dropped (#99).
   */
  private static void failOnUnknownEnvProviders(Map<String, String> configMap,
      Set<String> knownProviders) {
    Set<String> unknown = new TreeSet<>();
    for (String key : configMap.keySet()) {
      String provider = providerFromMarkerKey(key);
      if (provider != null && !knownProviders.contains(normalize(provider))) {
        unknown.add(provider);
      }
    }
    Validate.isTrue(unknown.isEmpty(), () -> new LlmProviderConfigurationException(
        "Configuration references unknown LLM provider(s) %s. Available providers: %s"
            .formatted(unknown, knownProviders)));
  }

  private static String providerFromMarkerKey(String key) {
    if (!key.startsWith(KEY_PREFIX)) {
      return null;
    }
    for (String marker : List.of("." + API_KEY, "." + BASE_URL)) {
      if (key.endsWith(marker) && key.length() > KEY_PREFIX.length() + marker.length()) {
        return key.substring(KEY_PREFIX.length(), key.length() - marker.length());
      }
    }
    return null;
  }

  private static Map<String, String> collectOptions(String prefix, Map<String, String> configMap) {
    Map<String, String> options = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configMap.entrySet()) {
      String key = entry.getKey();
      if (!key.startsWith(prefix)) {
        continue;
      }
      String suffix = key.substring(prefix.length());
      if (suffix.equals(API_KEY) || suffix.equals(BASE_URL) || suffix.equals(DEFAULT_MODEL)
          || suffix.equals(CONNECT_TIMEOUT)) {
        continue;
      }
      options.put(suffix, entry.getValue());
    }
    return options;
  }

  private static Duration parseConnectTimeout(String provider, String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_CONNECT_TIMEOUT;
    }
    try {
      // Shared grammar with RawProviderConfiguration/MapLlmProviderOptions: the same logical
      // property accepts the same forms whether set programmatically or auto-discovered here.
      return DurationParser.parse(value.trim());
    } catch (IllegalArgumentException cause) {
      throw new LlmProviderConfigurationException(
          "Provider '%s' option '%s' is not a valid duration (ISO-8601 like PT30S, or shorthand like 30s, 500ms)"
              .formatted(provider, CONNECT_TIMEOUT), cause);
    }
  }

  private static String normalize(String provider) {
    return StringUtils.lowerCase(StringUtils.trimToEmpty(provider));
  }
}
