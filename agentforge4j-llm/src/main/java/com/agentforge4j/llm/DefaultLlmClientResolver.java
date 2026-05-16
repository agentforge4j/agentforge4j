package com.agentforge4j.llm;

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
 * {@link #discover(ObjectMapper, Collection)} loads {@link LlmClientFactory} implementations via
 * JPMS {@link ServiceLoader}, pairs each with a matching {@link LlmClientConfiguration} by provider
 * id (case-insensitive), and constructs clients. {@link #resolve(String)} looks up a client or
 * fails with {@link IllegalArgumentException}.
 */
public final class DefaultLlmClientResolver implements LlmClientResolver {

  private final Map<String, LlmClient> providersByName;
  private static final System.Logger LOG =
      System.getLogger(DefaultLlmClientResolver.class.getName());

  /**
   * Creates a resolver backed by an explicit non-empty client list (typically used in tests).
   *
   * @param clients non-empty, no null elements, unique {@link LlmClient#getProviderName()} per
   *                entry
   * @throws IllegalArgumentException if validation fails
   */
  public DefaultLlmClientResolver(Collection<LlmClient> clients) {
    this.providersByName = buildProviderMap(clients);
  }

  /**
   * Discovers {@link LlmClientFactory} implementations and builds clients for each configuration
   * that matches a factory's provider id.
   * <p>
   * Factories without a matching configuration are skipped (warning logged). At least one client
   * must be created or {@link IllegalStateException} is thrown.
   *
   * @param objectMapper passed to each {@link LlmClientFactory#create}
   * @param configs      one or more {@link LlmClientConfiguration} entries (duplicate provider ids
   *                     fail)
   * @return resolver over all constructed clients
   * @throws IllegalStateException when no client could be built (missing provider modules or
   *                               configs)
   */
  public static DefaultLlmClientResolver discover(ObjectMapper objectMapper,
      Collection<LlmClientConfiguration> configs) {
    Validate.notNull(objectMapper, "objectMapper is null for LlmClientResolver");
    Validate.notNull(configs, "configs is null for LlmClientResolver");
    Map<String, LlmClientConfiguration> configByProvider = determineConfigsByProvider(configs);

    return new DefaultLlmClientResolver(Validate.notEmpty(
        resolveLlmClients(objectMapper, configByProvider), () ->
            new IllegalStateException(
                "No LLM clients could be created. Check that providerName JARs are on the classpath and configurations are provided.")));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LlmClient resolve(String provider) {
    String normalizedProvider = Validate.notBlank(normalizeProvider(provider),
        "LLM providerName must not be blank");
    return Validate.notNull(providersByName.get(normalizedProvider),
        "Unknown LLM providerName: '%s'. Available providers: %s".formatted(provider,
            providersByName.keySet()));
  }

  @Override
  public boolean isProviderAvailable(String provider) {
    String normalizedProvider = Validate.notBlank(normalizeProvider(provider),
        "LLM providerName must not be blank");
    return providersByName.containsKey(normalizedProvider);
  }

  @Override
  public List<String> listAvailableClients() {
    return providersByName.keySet().stream().map(DefaultLlmClientResolver::normalizeProvider)
        .collect(Collectors.toList());
  }

  private static Map<String, LlmClientConfiguration> determineConfigsByProvider(
      Collection<LlmClientConfiguration> configs) {
    configs.forEach(config ->
        Validate.notNull(config, "LLM client configuration entry must not be null")
    );
    return configs.stream()
        .collect(Collectors.toMap(
            config -> normalizeProvider(config.getProviderName()),
            Function.identity(),
            (existing, duplicate) -> {
              throw new IllegalArgumentException(
                  "Duplicate LLM client configuration for providerName: %s".formatted(
                      existing.getProviderName())
              );
            }
        ));
  }

  private static List<LlmClient> resolveLlmClients(ObjectMapper objectMapper,
      Map<String, LlmClientConfiguration> configByProvider) {
    List<LlmClient> clients = new ArrayList<>();
    ServiceLoader<LlmClientFactory> loader = ServiceLoader.load(LlmClientFactory.class,
        Thread.currentThread().getContextClassLoader());

    loader.forEach(factory ->
        addLlmClient(objectMapper, configByProvider, factory, clients));
    return clients;
  }

  private static void addLlmClient(ObjectMapper objectMapper,
      Map<String, LlmClientConfiguration> configByProvider, LlmClientFactory factory,
      List<LlmClient> clients) {
    String normalizedProvider = normalizeProvider(factory.getProviderName());
    LlmClientConfiguration config = configByProvider.get(normalizedProvider);
    if (config != null) {
      clients.add(factory.create(objectMapper, config));
    } else {
      LOG.log(System.Logger.Level.WARNING,
          "Provider ''{0}'' is on the classpath but has no configuration — skipping",
          normalizedProvider);
    }
  }

  private static Map<String, LlmClient> buildProviderMap(Collection<LlmClient> clients) {
    Map<String, LlmClient> map = new LinkedHashMap<>();
    for (LlmClient client : clients) {
      Validate.notNull(client, "LLM clients collection must not contain null entries");
      String normalizedProvider = normalizeProvider(client.getProviderName());
      Validate.notBlank(normalizedProvider,
          "LLM client providerName name must not be blank for client: %s".formatted(
              client.getClass().getName()));
      LlmClient existingClient = map.putIfAbsent(normalizedProvider, client);
      Validate.isTrue(existingClient == null, () -> new IllegalStateException(
          "Duplicate LLM providerName name '%s' for clients: %s and %s".formatted(
              normalizedProvider,
              existingClient.getClass().getName(), client.getClass().getName())));
    }
    return Map.copyOf(map);
  }

  private static String normalizeProvider(String provider) {
    return StringUtils.lowerCase(StringUtils.trimToEmpty(provider));
  }
}
