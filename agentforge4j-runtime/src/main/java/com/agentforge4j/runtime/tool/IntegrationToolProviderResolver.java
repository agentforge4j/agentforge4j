// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationRepository;
import com.agentforge4j.core.spi.integration.ToolProviderFactory;
import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.util.Validate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single OSS {@link ToolProviderResolver}: it merges two tool sources into one capability index
 * at construction (tool-load) time —
 * <ol>
 *   <li>the active {@link IntegrationDefinition}s of an {@link IntegrationRepository}, each
 *   materialized into a {@link ToolProvider} via a {@link ToolProviderFactory}; and</li>
 *   <li>pre-built {@link ToolProvider} instances supplied directly — for example the MCP providers
 *   the Spring starter builds from {@code agentforge4j.mcp.servers}, or providers passed to
 *   {@code withToolProviders}.</li>
 * </ol>
 *
 * <p>Materializing eagerly means MCP connections are established once, when the runtime is
 * assembled, rather than per resolve. It fails fast with {@link CapabilityResolutionException} as
 * soon as two sources — in either combination — expose the same capability, naming the capability
 * and both contributing sources, so an ambiguous configuration is rejected up front. It never
 * resolves ambiguity by silent first-wins; {@link #resolve} is then an O(1) lookup.
 *
 * <p>The merged set is read once at construction. Rebuilding the index when integrations change
 * (reload) is wired by a later phase; this resolver holds an immutable snapshot.
 */
public final class IntegrationToolProviderResolver implements ToolProviderResolver {

  private final Map<String, ResolvedTool> byCapability;

  /**
   * Merges the repository's active integrations and the supplied pre-built providers into one
   * capability index, rejecting cross-source duplicates up front.
   *
   * @param repository        source of active integrations; never {@code null} (may be empty)
   * @param factory           realises each active integration as a tool provider; never
   *                          {@code null}
   * @param preBuiltProviders providers supplied directly, not via the repository; never
   *                          {@code null} (may be empty)
   *
   * @throws CapabilityResolutionException if two sources expose the same capability
   */
  public IntegrationToolProviderResolver(IntegrationRepository repository,
      ToolProviderFactory factory, List<ToolProvider> preBuiltProviders) {
    Validate.notNull(repository, "repository must not be null");
    Validate.notNull(factory, "factory must not be null");
    Validate.notNull(preBuiltProviders, "preBuiltProviders must not be null");
    Map<String, ResolvedTool> resolved = new LinkedHashMap<>();
    Map<String, String> sourceByCapability = new LinkedHashMap<>();
    for (IntegrationDefinition definition : repository.findActive()) {
      index(factory.create(definition), "integration '%s'".formatted(definition.id()),
          resolved, sourceByCapability);
    }
    for (ToolProvider provider : preBuiltProviders) {
      index(provider, "provider '%s'".formatted(provider.providerId()),
          resolved, sourceByCapability);
    }
    this.byCapability = Collections.unmodifiableMap(resolved);
  }

  /**
   * Indexes one provider's tools by capability, rejecting a capability already claimed by an
   * earlier source (definition or provider) and naming both sources in the failure.
   */
  private static void index(ToolProvider provider, String source,
      Map<String, ResolvedTool> resolved, Map<String, String> sourceByCapability) {
    for (ToolDescriptor descriptor : provider.listTools()) {
      String capability = descriptor.capability();
      String previousSource = sourceByCapability.putIfAbsent(capability, source);
      Validate.isTrue(previousSource == null, () -> new CapabilityResolutionException(
          "Capability '%s' is ambiguous: exposed by %s and %s".formatted(
              capability, previousSource, source)));
      resolved.put(capability, new ResolvedTool(provider, descriptor));
    }
  }

  @Override
  public ResolvedTool resolve(String capability, ToolScope scope) {
    // scope is intentionally ignored: OSS carries no tenant/binding model and resolves a capability
    // identically regardless of workflow/run. Binding-aware resolution by scope is the embedding
    // application's concern, not this OSS resolver's.
    Validate.notBlank(capability, "capability must not be blank");
    return Validate.notNull(byCapability.get(capability),
        () -> new CapabilityResolutionException(
            "No active integration or provider exposes capability '%s'".formatted(capability)));
  }

  @Override
  public List<ToolDescriptor> available(ToolScope scope) {
    return byCapability.values().stream().map(ResolvedTool::descriptor).toList();
  }
}
