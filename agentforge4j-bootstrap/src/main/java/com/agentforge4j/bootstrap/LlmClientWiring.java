package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Assembles the list of {@link com.agentforge4j.llm.api.LlmClient} instances from programmatic
 * {@link LlmProviderConfig} overrides and environment / system-property auto-discovery.
 *
 * <p>Internal — not part of the public API.
 */
final class LlmClientWiring {

  private static final System.Logger LOGGER =
      System.getLogger(LlmClientWiring.class.getName());

  private static final String PROVIDER_OPENAI = "openai";
  private static final String PROVIDER_CLAUDE = "claude";
  private static final String PROVIDER_OLLAMA = "ollama";
  private static final String PROVIDER_VLLM = "vllm";
  private static final String PROVIDER_GEMINI = "gemini";
  private static final String PROVIDER_MISTRAL = "mistral";
  private static final String PROVIDER_AZURE_OPENAI = "azure-openai";
  private static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
  private static final String PROVIDER_BEDROCK = "bedrock";

  private static final List<String> KNOWN_LLM_PROVIDERS = List.of(
      PROVIDER_OPENAI,
      PROVIDER_CLAUDE,
      PROVIDER_OLLAMA,
      PROVIDER_VLLM,
      PROVIDER_GEMINI,
      PROVIDER_MISTRAL,
      PROVIDER_AZURE_OPENAI,
      PROVIDER_OPENAI_COMPATIBLE,
      PROVIDER_BEDROCK);

  private static final Map<String, Supplier<LlmProviderConfig.ProviderBuilder>> PROVIDER_BUILDERS =
      Map.ofEntries(
          Map.entry(PROVIDER_OPENAI, LlmProviderConfig::openai),
          Map.entry(PROVIDER_CLAUDE, LlmProviderConfig::claude),
          Map.entry(PROVIDER_OLLAMA, LlmProviderConfig::ollama),
          Map.entry(PROVIDER_VLLM, LlmProviderConfig::vllm),
          Map.entry(PROVIDER_GEMINI, LlmProviderConfig::gemini),
          Map.entry(PROVIDER_MISTRAL, LlmProviderConfig::mistral),
          Map.entry(PROVIDER_AZURE_OPENAI, LlmProviderConfig::azureOpenAi),
          Map.entry(PROVIDER_OPENAI_COMPATIBLE, LlmProviderConfig::openAiCompatible),
          Map.entry(PROVIDER_BEDROCK, LlmProviderConfig::bedrock));

  /**
   * Providers that do not require an API key and are included by default even without explicit
   * configuration. Callers on a local network can use these providers without any credentials.
   */
  private static final Set<String> NO_API_KEY_PROVIDERS =
      Set.of(PROVIDER_OLLAMA, PROVIDER_VLLM);

  private LlmClientWiring() {
    throw new UnsupportedOperationException("static utility");
  }

  static List<LlmClient> buildLlmClients(
      ObjectMapper objectMapper, Map<String, LlmProviderConfig> programmaticProviders) {
    Map<String, LlmProviderConfig> effectiveConfigs =
        resolveEffectiveLlmProviderConfigs(programmaticProviders);
    if (effectiveConfigs.isEmpty()) {
      return List.of();
    }
    List<LlmClientConfiguration> configurations = new ArrayList<>();
    for (LlmProviderConfig providerConfig : effectiveConfigs.values()) {
      configurations.add(new ProviderConfigAdapter(providerConfig));
    }
    try {
      DefaultLlmClientResolver resolver =
          DefaultLlmClientResolver.discover(objectMapper, configurations);
      return resolver.listAvailableClients().stream().map(resolver::resolve).toList();
    } catch (IllegalStateException exception) {
      LOGGER.log(System.Logger.Level.WARNING,
          "LLM client discovery failed; no providers will be available at runtime: {0}",
          exception.getMessage());
      return List.of();
    }
  }

  static Map<String, LlmProviderConfig> resolveEffectiveLlmProviderConfigs(
      Map<String, LlmProviderConfig> programmaticProviders) {
    Map<String, String> config = ConfigReader.read();
    Map<String, LlmProviderConfig> effectiveConfigs = new LinkedHashMap<>();
    for (String provider : KNOWN_LLM_PROVIDERS) {
      if (programmaticProviders.containsKey(provider)) {
        effectiveConfigs.put(provider, programmaticProviders.get(provider));
      } else {
        LlmProviderConfig providerConfig = buildLlmProviderConfigFromConfig(provider, config);
        if (providerConfig != null) {
          effectiveConfigs.put(provider, providerConfig);
        } else if (NO_API_KEY_PROVIDERS.contains(provider)) {
          effectiveConfigs.put(
              provider, PROVIDER_BUILDERS.get(provider).get().defaults().build());
        }
      }
    }
    return effectiveConfigs;
  }

  private static LlmProviderConfig buildLlmProviderConfigFromConfig(
      String provider,
      Map<String, String> config) {
    String prefix = "agentforge4j.llm." + provider + ".";
    String apiKey = config.get(prefix + "api-key");
    String baseUrl = config.get(prefix + "base-url");
    String defaultModel = config.get(prefix + "default-model");
    String timeoutStr = config.get(prefix + "connect-timeout-seconds");
    Duration connectTimeout = determineConnectTimeout(provider, timeoutStr);
    if (providerRequiresApiKey(provider) && apiKey == null) {
      return null;
    }
    if (!providerRequiresApiKey(provider) &&
        !hasAnyConfigs(apiKey, baseUrl, defaultModel, timeoutStr)) {
      return null;
    }
    return createBuilder(provider, apiKey, baseUrl, defaultModel, connectTimeout);
  }

  private static boolean hasAnyConfigs(String apiKey, String baseUrl, String defaultModel,
      String timeoutStr) {
    return apiKey != null || baseUrl != null || defaultModel != null || timeoutStr != null;
  }

  private static LlmProviderConfig createBuilder(String provider, String apiKey,
      String baseUrl, String defaultModel, Duration connectTimeout) {
    LlmProviderConfig.ProviderBuilder builder = PROVIDER_BUILDERS.get(provider).get().defaults();
    builder.apiKey(apiKey);
    if (baseUrl != null) {
      builder.baseUrl(baseUrl);
    }
    if (defaultModel != null) {
      builder.defaultModel(defaultModel);
    }
    if (connectTimeout != null) {
      builder.connectTimeout(connectTimeout);
    }
    return builder.build();
  }

  private static Duration determineConnectTimeout(String provider, String timeoutStr) {
    Duration connectTimeout = null;
    if (timeoutStr != null) {
      try {
        connectTimeout = Duration.ofSeconds(Long.parseLong(timeoutStr));
      } catch (NumberFormatException exception) {
        throw new IllegalStateException(
            "Invalid value for agentforge4j.llm.%s.connect-timeout-seconds: '%s' — expected a whole number of seconds."
                .formatted(provider, timeoutStr),
            exception);
      }
    }
    return connectTimeout;
  }

  private static boolean providerRequiresApiKey(String provider) {
    return !NO_API_KEY_PROVIDERS.contains(provider);
  }
}
