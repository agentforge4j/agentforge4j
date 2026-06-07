package com.agentforge4j.runtime.tool;

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
 * OSS default {@link ToolProviderResolver} that indexes a fixed list of providers by capability at
 * construction (tool-load) time. It fails fast with {@link CapabilityResolutionException} as soon
 * as two providers expose the same capability — naming both — so an ambiguous configuration is
 * rejected when the runtime is assembled, not midway through a run. It never resolves ambiguity by
 * silent first-wins; {@link #resolve} is then an O(1) lookup.
 */
public final class ProviderScanningResolver implements ToolProviderResolver {

  private final Map<String, ResolvedTool> byCapability;

  /**
   * Indexes every provider's tools by capability, rejecting duplicates up front.
   *
   * @param providers the providers to index; never {@code null}
   *
   * @throws CapabilityResolutionException if two providers expose the same capability
   */
  public ProviderScanningResolver(List<ToolProvider> providers) {
    Validate.notNull(providers, "providers must not be null");
    Map<String, ResolvedTool> resolved = new LinkedHashMap<>();
    for (ToolProvider provider : providers) {
      for (ToolDescriptor descriptor : provider.listTools()) {
        ResolvedTool existing =
            resolved.putIfAbsent(descriptor.capability(), new ResolvedTool(provider, descriptor));
        Validate.isTrue(existing == null, () -> new CapabilityResolutionException(
            "Capability '%s' is ambiguous: exposed by providers '%s' and '%s'".formatted(
                descriptor.capability(), existing.provider().providerId(),
                provider.providerId())));
      }
    }
    this.byCapability = Collections.unmodifiableMap(resolved);
  }

  @Override
  public ResolvedTool resolve(String capability, ToolScope scope) {
    // scope is intentionally ignored: OSS carries no tenant/binding model and resolves a capability
    // identically regardless of workflow/run. Binding-aware resolution by scope is the platform's
    // PersistentMcpServerRegistry (workflow- vs tenant-binding precedence).
    Validate.notBlank(capability, "capability must not be blank");
    return Validate.notNull(byCapability.get(capability),
        () -> new CapabilityResolutionException(
            "No provider exposes capability '%s'".formatted(capability)));
  }

  @Override
  public List<ToolDescriptor> available(ToolScope scope) {
    return List.copyOf(byCapability.values().stream().map(ResolvedTool::descriptor).toList());
  }
}
