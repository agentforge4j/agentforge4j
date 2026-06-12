package com.agentforge4j.bootstrap;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.integration.SecretResolver;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.integration.ToolProviderFactoryContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * {@link ToolProviderFactory} that routes each {@link IntegrationDefinition} to the
 * {@link IntegrationToolProviderFactory} contribution matching its {@link IntegrationType}.
 * <p>
 * {@link #discover(ObjectMapper)} loads contributions via {@link ServiceLoader} (mirroring
 * {@code DefaultLlmClientResolver}'s {@code LlmClientFactory} discovery), so provider modules such
 * as {@code agentforge4j-mcp} plug in by being on the classpath — this module declares no concrete
 * provider dependency. Two contributions claiming the same type fail construction; a definition
 * whose type has no contribution fails {@link #create} naming the type and the missing module.
 */
public final class ServiceLoaderToolProviderFactory implements ToolProviderFactory {

  private static final System.Logger LOG =
      System.getLogger(ServiceLoaderToolProviderFactory.class.getName());

  private final Map<IntegrationType, IntegrationToolProviderFactory> contributionsByType;
  private final ToolProviderFactoryContext context;

  /**
   * Creates an aggregator over explicit contributions (typically used in tests).
   *
   * @param contributions  no null elements, at most one contribution per {@link IntegrationType}
   * @param objectMapper   the single shared Jackson mapper threaded into each contribution; must
   *                       not be {@code null}
   * @param secretResolver the secret-reference resolver threaded into each contribution; must not
   *                       be {@code null}
   *
   * @throws IllegalStateException if two contributions claim the same type
   */
  public ServiceLoaderToolProviderFactory(
      Collection<IntegrationToolProviderFactory> contributions, ObjectMapper objectMapper,
      SecretResolver secretResolver) {
    this.contributionsByType = buildContributionMap(
        Validate.notNull(contributions, "contributions must not be null"));
    this.context = new ToolProviderFactoryContext(objectMapper, secretResolver);
  }

  /**
   * Discovers {@link IntegrationToolProviderFactory} contributions on the classpath.
   * <p>
   * An empty result is not an error: the aggregator only fails when asked to realise a definition
   * whose type has no contribution.
   *
   * @param objectMapper   the single shared Jackson mapper threaded into each contribution; must
   *                       not be {@code null}
   * @param secretResolver the secret-reference resolver threaded into each contribution; must not
   *                       be {@code null}
   *
   * @return aggregator over all discovered contributions
   */
  public static ServiceLoaderToolProviderFactory discover(ObjectMapper objectMapper,
      SecretResolver secretResolver) {
    List<IntegrationToolProviderFactory> contributions = new ArrayList<>();
    ServiceLoader<IntegrationToolProviderFactory> loader = ServiceLoader.load(
        IntegrationToolProviderFactory.class, Thread.currentThread().getContextClassLoader());
    loader.forEach(contributions::add);
    LOG.log(System.Logger.Level.INFO,
        "Discovered {0} integration tool provider factory contribution(s)", contributions.size());
    return new ServiceLoaderToolProviderFactory(contributions, objectMapper, secretResolver);
  }

  @Override
  public ToolProvider create(IntegrationDefinition definition) {
    Validate.notNull(definition, "definition must not be null");
    IntegrationToolProviderFactory contribution =
        Validate.notNull(contributionsByType.get(definition.type()),
            () -> new IllegalStateException(
                "No IntegrationToolProviderFactory is registered for integration type %s (integration '%s'). Check that the provider module for this type (for example agentforge4j-mcp for the MCP types) is on the classpath."
                    .formatted(definition.type(), definition.id())));
    return contribution.create(definition, context);
  }

  private static Map<IntegrationType, IntegrationToolProviderFactory> buildContributionMap(
      Collection<IntegrationToolProviderFactory> contributions) {
    Map<IntegrationType, IntegrationToolProviderFactory> byType =
        new EnumMap<>(IntegrationType.class);
    for (IntegrationToolProviderFactory contribution : contributions) {
      Validate.notNull(contribution, "contributions must not contain null entries");
      IntegrationType type = Validate.notNull(contribution.supportedType(),
          "supportedType() must not return null for contribution: %s"
              .formatted(contribution.getClass().getName()));
      IntegrationToolProviderFactory existing = byType.putIfAbsent(type, contribution);
      Validate.isTrue(existing == null, () -> new IllegalStateException(
          "Duplicate IntegrationToolProviderFactory for type %s: %s and %s".formatted(
              type, existing.getClass().getName(), contribution.getClass().getName())));
    }
    return Collections.unmodifiableMap(byType);
  }
}
