// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.EnvSystemPropertyLlmSecretResolver;
import com.agentforge4j.llm.ConfigModelTierResolver;
import com.agentforge4j.core.spi.integration.IntegrationConfigLoader;
import com.agentforge4j.core.spi.integration.MutableIntegrationRepository;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures AgentForge4j by delegating to {@link AgentForge4jBootstrap}.
 *
 * <p>Exposes a single {@link AgentForge4j} bean. All assembled components are
 * accessible via {@link AgentForge4j#components()}. Spring beans for individual components can be
 * registered by the application as needed:
 *
 * <pre>{@code
 * public WorkflowRuntime workflowRuntime(AgentForge4j agentForge4j) {
 *     return agentForge4j.runtime();
 * }
 * }</pre>
 *
 * <p>Register the method above as a Spring bean when needed.
 *
 * <p>Maps {@link AgentForge4jProperties} to bootstrap builder calls. All beans use
 * {@link ConditionalOnMissingBean} — the {@link AgentForge4j} bean can be overridden by the
 * application.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@EnableConfigurationProperties({AgentForge4jProperties.class, LlmCacheSettings.class,
    ModelTierProperties.class})
public class BootstrapAutoConfiguration {

  /**
   * Assembles and exposes the full AgentForge4j facade.
   *
   * @param properties           bound AgentForge4j properties; must not be {@code null}
   * @param cacheSettings        LLM prompt cache settings; must not be {@code null}
   * @param objectMapperProvider optional Jackson mapper from the context
   * @param llmConfigurations    optional provider configuration beans registered before this
   *                             config
   *
   * @return assembled facade; never {@code null}
   */
  @Bean
  @ConditionalOnMissingBean
  public AgentForge4j agentForge4j(
      AgentForge4jProperties properties,
      LlmCacheSettings cacheSettings,
      ModelTierProperties modelTierProperties,
      ObjectProvider<ObjectMapper> objectMapperProvider,
      ObjectProvider<List<LlmClientConfiguration>> llmConfigurations,
      ObjectProvider<List<ToolProvider>> toolProviders,
      ObjectProvider<ToolPolicy> toolPolicy,
      ObjectProvider<ToolExecutionOptions> toolExecutionOptions,
      ObjectProvider<IntegrationConfigLoader> integrationConfigLoader,
      ObjectProvider<MutableIntegrationRepository> integrationRepository,
      ObjectProvider<ToolProviderFactory> toolProviderFactory) {
    AgentForge4jBootstrap.Builder builder = AgentForge4jBootstrap.defaults();
    applyProperties(builder, properties);
    applyModelTiers(builder, modelTierProperties);
    builder.withCacheEnabled(cacheSettings.enabled());
    List<LlmClientConfiguration> configurations = llmConfigurations.getIfAvailable(List::of);
    if (!configurations.isEmpty()) {
      ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
      builder.withLlmClientResolver(DefaultLlmClientResolver.discover(mapper, configurations,
          new EnvSystemPropertyLlmSecretResolver()));
    } else {
      builder.withLlmClientResolver(new DefaultLlmClientResolver(List.of()));
    }
    applyToolSupport(builder, toolProviders, toolPolicy, toolExecutionOptions);
    applyIntegrationOverrides(builder, integrationConfigLoader, integrationRepository,
        toolProviderFactory);
    return builder.build();
  }

  /**
   * Forwards application-registered integration component beans to the bootstrap builder. Each is
   * optional: when absent, the bootstrap defaults (filesystem loader over
   * {@code agentforge4j.integrations.dir}, in-memory repository, ServiceLoader-discovered factory
   * contributions) apply.
   *
   * @param builder                 bootstrap builder; must not be {@code null}
   * @param integrationConfigLoader optional loader bean overriding the filesystem loader
   * @param integrationRepository   optional repository bean overriding the in-memory repository
   * @param toolProviderFactory     optional factory bean overriding the discovered aggregator
   */
  private static void applyIntegrationOverrides(AgentForge4jBootstrap.Builder builder,
      ObjectProvider<IntegrationConfigLoader> integrationConfigLoader,
      ObjectProvider<MutableIntegrationRepository> integrationRepository,
      ObjectProvider<ToolProviderFactory> toolProviderFactory) {
    IntegrationConfigLoader loader = integrationConfigLoader.getIfAvailable();
    if (loader != null) {
      builder.withIntegrationConfigLoader(loader);
    }
    MutableIntegrationRepository repository = integrationRepository.getIfAvailable();
    if (repository != null) {
      builder.withIntegrationRepository(repository);
    }
    ToolProviderFactory factory = toolProviderFactory.getIfAvailable();
    if (factory != null) {
      builder.withToolProviderFactory(factory);
    }
  }

  private static void applyToolSupport(AgentForge4jBootstrap.Builder builder,
      ObjectProvider<List<ToolProvider>> toolProviders,
      ObjectProvider<ToolPolicy> toolPolicy,
      ObjectProvider<ToolExecutionOptions> toolExecutionOptions) {
    List<ToolProvider> providers = toolProviders.getIfAvailable(List::of);
    if (providers.isEmpty()) {
      return;
    }
    builder.withToolProviders(providers);
    ToolPolicy policy = toolPolicy.getIfAvailable();
    if (policy != null) {
      builder.withToolPolicy(policy);
    }
    ToolExecutionOptions options = toolExecutionOptions.getIfAvailable();
    if (options != null) {
      builder.withToolExecutionOptions(options);
    }
  }

  private static void applyProperties(AgentForge4jBootstrap.Builder builder,
      AgentForge4jProperties properties) {
    Validate.notNull(properties, "AgentForge4j properties must not be null");
    if (StringUtils.isNotBlank(properties.agentsPath())) {
      builder.withAgentsDir(Path.of(properties.agentsPath()));
    }
    if (StringUtils.isNotBlank(properties.workflowsPath())) {
      builder.withWorkflowsDir(Path.of(properties.workflowsPath()));
    }
    if (properties.integrations() != null
        && StringUtils.isNotBlank(properties.integrations().dir())) {
      builder.withIntegrationsDir(Path.of(properties.integrations().dir()));
    }
    if (properties.maxNestingDepth() != null) {
      builder.withMaxNestingDepth(properties.maxNestingDepth());
    }
    builder.withLoadShippedAgents(properties.loadShippedAgents());
    builder.withLoadShippedWorkflows(properties.loadShippedWorkflows());
  }

  /**
   * Builds a {@link ConfigModelTierResolver} from the shipped defaults merged with any
   * {@code agentforge4j.llm.model-tiers.*} overrides and registers it on the builder. When no
   * overrides are configured the bootstrap default (shipped defaults plus env/system-property
   * overrides) is left in place.
   *
   * @param builder    bootstrap builder; must not be {@code null}
   * @param properties bound model-tier properties; must not be {@code null}
   */
  private static void applyModelTiers(AgentForge4jBootstrap.Builder builder,
      ModelTierProperties properties) {
    Validate.notNull(properties, "ModelTier properties must not be null");
    Map<String, Map<String, String>> configured = properties.modelTiers();
    if (configured == null || configured.isEmpty()) {
      return;
    }
    Map<String, Map<ModelTier, String>> overrides = new HashMap<>();
    for (Map.Entry<String, Map<String, String>> providerEntry : configured.entrySet()) {
      if (providerEntry.getValue() == null) {
        continue;
      }
      Map<ModelTier, String> byTier = new EnumMap<>(ModelTier.class);
      for (Map.Entry<String, String> tierEntry : providerEntry.getValue().entrySet()) {
        byTier.put(parseTier(tierEntry.getKey(), providerEntry.getKey()), tierEntry.getValue());
      }
      overrides.put(providerEntry.getKey(), byTier);
    }
    builder.withModelTierResolver(
        ConfigModelTierResolver.withShippedDefaultsAndOverrides(overrides));
  }

  private static ModelTier parseTier(String tierName, String provider) {
    try {
      return ModelTier.valueOf(tierName.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          ("Invalid tier '%s' for provider '%s' under agentforge4j.llm.model-tiers — "
              + "valid tiers: LITE, STANDARD, POWERFUL").formatted(tierName, provider),
          exception);
    }
  }
}
