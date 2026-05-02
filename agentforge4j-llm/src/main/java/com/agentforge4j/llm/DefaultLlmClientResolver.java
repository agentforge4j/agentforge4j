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
 * Default implementation of {@link LlmClientResolver} that discovers and manages LLM providers.
 * <p>
 * This resolver discovers providerName factories via JPMS {@link ServiceLoader} and instantiates
 * clients for each configured providerName. Provider names are normalized to lowercase and matched
 * against configuration keys. The resolver maintains an immutable map of available providers and
 * fails fast if no providers are configured or if a requested providerName is not available.
 */
public final class DefaultLlmClientResolver implements LlmClientResolver {

  private final Map<String, LlmClient> providersByName;
  private static final System.Logger LOG =
      System.getLogger(DefaultLlmClientResolver.class.getName());

  /**
   * Creates a resolver with the provided LLM clients.
   *
   * @param clients the collection of LLM clients to manage; must not be empty or contain nulls
   * @throws IllegalArgumentException if clients is empty, contains null entries, or contains
   *                                  duplicate providerName names
   */
  public DefaultLlmClientResolver(Collection<LlmClient> clients) {
    this.providersByName = buildProviderMap(clients);
  }

  /**
   * Discovers LLM providers by loading factories via {@link ServiceLoader} and instantiating
   * clients for each providerName that has a matching configuration.
   * <p>
   * Provider discovery process:
   * <ol>
   *   <li>Discovers all {@link LlmClientFactory} implementations on the classpath</li>
   *   <li>For each factory, looks up a matching configuration by normalized providerName name</li>
   *   <li>Creates a client for each factory with a configuration</li>
   *   <li>Logs a warning for factories with no configuration</li>
   * </ol>
   *
   * @param objectMapper the JSON mapper for response parsing
   * @param configs      the collection of providerName configurations
   * @return a resolver managing all discovered clients
   * @throws IllegalStateException if no LLM clients can be created; check that providerName JARs are on
   *                               the classpath and configurations are provided
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
   * Resolves an LLM client for the given providerName name.
   *
   * @param provider the providerName name (case-insensitive)
   * @return the LLM client for this providerName
   * @throws IllegalArgumentException if the providerName is not found or the name is blank
   */
  @Override
  public LlmClient resolve(String provider) {
    String normalizedProvider = Validate.notBlank(normalizeProvider(provider),
        "LLM providerName must not be blank");
    return Validate.notNull(providersByName.get(normalizedProvider),
        "Unknown LLM providerName: '%s'. Available providers: %s".formatted(provider,
            providersByName.keySet()));
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
          "Duplicate LLM providerName name '%s' for clients: %s and %s".formatted(normalizedProvider,
              existingClient.getClass().getName(), client.getClass().getName())));
    }
    return Map.copyOf(map);
  }

  private static String normalizeProvider(String provider) {
    return StringUtils.lowerCase(StringUtils.trimToEmpty(provider));
  }
}
