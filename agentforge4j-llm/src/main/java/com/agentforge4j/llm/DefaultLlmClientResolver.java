// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Default {@link LlmClientResolver}: builds an immutable map of provider id to {@link LlmClient}.
 * <p>
 * {@link #discover(ObjectMapper, Collection, LlmSecretResolver)} loads {@link LlmClientFactory} implementations via
 * JPMS {@link ServiceLoader}, pairs each with a matching {@link LlmClientConfiguration} by provider id
 * (case-insensitive), and constructs clients. {@link #resolve(String)} looks up a client or fails with
 * {@link IllegalArgumentException}.
 */
public final class DefaultLlmClientResolver implements LlmClientResolver {

  private final Map<String, LlmClient> providersByName;

  /**
   * Creates a resolver backed by an explicit non-empty client list (typically used in tests).
   *
   * @param clients non-empty, no null elements, unique {@link LlmClient#getProviderName()} per entry
   *
   * @throws IllegalArgumentException if validation fails
   */
  public DefaultLlmClientResolver(Collection<LlmClient> clients) {
    this.providersByName = buildProviderMap(clients);
  }

  /**
   * Discovers {@link LlmClientFactory} implementations and builds clients for each configuration that matches a
   * factory's provider id, resolving each provider's credential reference via {@code secretResolver}. Each client is
   * constructed through {@link LlmClientFactory#create(LlmClientFactoryContext)}.
   * <p>
   * Factories without a matching configuration are skipped (warning logged). At least one client must be created or
   * {@link IllegalStateException} is thrown.
   *
   * @param objectMapper   passed to each factory
   * @param configs        one or more {@link LlmClientConfiguration} entries (duplicate provider ids fail)
   * @param secretResolver resolver for credential references; must not be {@code null}
   *
   * @return resolver over all constructed clients
   *
   * @throws IllegalStateException when no client could be built (missing provider modules or configs)
   */
  public static DefaultLlmClientResolver discover(ObjectMapper objectMapper,
      Collection<LlmClientConfiguration> configs, LlmSecretResolver secretResolver) {
    return build(objectMapper, configs, secretResolver, loadFactories());
  }

  /**
   * Discovery core, separated from {@link ServiceLoader} loading so it can be driven with an explicit factory list in
   * tests. Fails fast ({@link LlmProviderConfigurationException}) on a duplicate provider factory or a configuration
   * whose provider has no matching factory; factories without a matching configuration are simply not constructed.
   *
   * @param objectMapper   passed to each factory
   * @param configs        provider configurations (duplicate provider ids fail)
   * @param secretResolver resolver for credential references; must not be {@code null}
   * @param factories      the discovered factories
   *
   * @return resolver over all constructed clients
   */
  static DefaultLlmClientResolver build(ObjectMapper objectMapper, Collection<LlmClientConfiguration> configs,
      LlmSecretResolver secretResolver, List<LlmClientFactory> factories) {
    Validate.notNull(objectMapper, "objectMapper is null for LlmClientResolver");
    Validate.notNull(configs, "configs is null for LlmClientResolver");
    Validate.notNull(secretResolver, "secretResolver is null for LlmClientResolver");
    Validate.notNull(factories, "factories is null for LlmClientResolver");
    Map<String, LlmClientConfiguration> configByProvider = determineConfigsByProvider(configs);
    Map<String, LlmClientFactory> factoriesByProvider = indexFactories(factories);

    validateConfigsHasFactories(configByProvider, factoriesByProvider);

    List<LlmClient> clients = createLlmClientList(objectMapper, secretResolver, factoriesByProvider, configByProvider);
    return new DefaultLlmClientResolver(Validate.notEmpty(clients, () ->
        new IllegalStateException(
            "No LLM clients could be created. Check that providerName JARs are on the classpath and configurations are provided.")));
  }

  private static List<LlmClient> createLlmClientList(ObjectMapper objectMapper, LlmSecretResolver secretResolver,
      Map<String, LlmClientFactory> factoriesByProvider, Map<String, LlmClientConfiguration> configByProvider) {
    List<LlmClient> clients = new ArrayList<>();
    for (LlmClientFactory factory : factoriesByProvider.values()) {
      LlmClientConfiguration config = configByProvider.get(normalizeProvider(factory.getProviderName()));
      if (config != null) {
        clients.add(factory.create(new LlmClientFactoryContext(objectMapper, config, secretResolver)));
      }
    }
    return clients;
  }

  private static void validateConfigsHasFactories(Map<String, LlmClientConfiguration> configByProvider,
      Map<String, LlmClientFactory> factoriesByProvider) {
    for (String provider : configByProvider.keySet()) {
      if (!factoriesByProvider.containsKey(provider)) {
        throw new LlmProviderConfigurationException(
            "LLM configuration for provider '%s' has no matching factory on the classpath. Available providers: %s"
                .formatted(provider, factoriesByProvider.keySet()));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LlmClient resolve(String provider) {
    String normalizedProvider = Validate.notBlank(normalizeProvider(provider), "LLM providerName must not be blank");
    return Validate.notNull(providersByName.get(normalizedProvider),
        "Unknown LLM providerName: '%s'. Available providers: %s".formatted(provider, providersByName.keySet()));
  }

  @Override
  public boolean isProviderAvailable(String provider) {
    String normalizedProvider = Validate.notBlank(normalizeProvider(provider), "LLM providerName must not be blank");
    return providersByName.containsKey(normalizedProvider);
  }

  @Override
  public List<String> listAvailableClients() {
    return providersByName.keySet().stream().map(DefaultLlmClientResolver::normalizeProvider).toList();
  }

  private static Map<String, LlmClientConfiguration> determineConfigsByProvider(
      Collection<LlmClientConfiguration> configs) {
    configs.forEach(config -> Validate.notNull(config, "LLM client configuration entry must not be null")
    );
    return configs.stream()
        .collect(Collectors.toMap(
            config -> normalizeProvider(config.getProviderName()),
            Function.identity(),
            (existing, duplicate) -> {
              throw new IllegalArgumentException(
                  "Duplicate LLM client configuration for providerName: %s".formatted(existing.getProviderName())
              );
            }
        ));
  }

  private static List<LlmClientFactory> loadFactories() {
    List<LlmClientFactory> factories = new ArrayList<>();
    ServiceLoader.load(LlmClientFactory.class, Thread.currentThread().getContextClassLoader())
        .forEach(factories::add);
    return factories;
  }

  private static Map<String, LlmClientFactory> indexFactories(List<LlmClientFactory> factories) {
    Map<String, LlmClientFactory> byProvider = new LinkedHashMap<>();
    for (LlmClientFactory factory : factories) {
      String provider = normalizeProvider(factory.getProviderName());
      LlmClientFactory existing = byProvider.putIfAbsent(provider, factory);
      if (existing != null) {
        throw new LlmProviderConfigurationException(
            "Duplicate LLM provider factory for '%s': %s and %s".formatted(
                provider, existing.getClass().getName(), factory.getClass().getName()));
      }
    }
    return byProvider;
  }

  private static Map<String, LlmClient> buildProviderMap(Collection<LlmClient> clients) {
    Map<String, LlmClient> map = new LinkedHashMap<>();
    for (LlmClient client : clients) {
      Validate.notNull(client, "LLM clients collection must not contain null entries");
      String normalizedProvider = normalizeProvider(client.getProviderName());
      Validate.notBlank(normalizedProvider,
          "LLM client providerName name must not be blank for client: %s".formatted(client.getClass().getName()));
      LlmClient existingClient = map.putIfAbsent(normalizedProvider, client);
      Validate.isTrue(existingClient == null, () -> new IllegalStateException(
          "Duplicate LLM providerName name '%s' for clients: %s and %s".formatted(
              normalizedProvider, existingClient.getClass().getName(), client.getClass().getName())));
    }
    return Map.copyOf(map);
  }

  private static String normalizeProvider(String provider) {
    return StringUtils.lowerCase(StringUtils.trimToEmpty(provider));
  }
}
