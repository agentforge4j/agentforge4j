// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import java.util.List;

/**
 * The sole resolver of a logical capability to the provider that fulfils it.
 *
 * <p>Implementations must never resolve ambiguity by silent first-wins: when no provider, or more
 * than one,
 * fulfils a capability they fail fast with {@link CapabilityResolutionException}.
 */
public interface ToolProviderResolver {

  /**
   * Resolves a capability to its provider and descriptor for the given scope.
   *
   * @param capability non-blank capability id
   * @param scope      workflow/run scope used by binding-aware resolvers
   *
   * @return the resolved tool
   *
   * @throws CapabilityResolutionException if no provider, or more than one, fulfils the capability
   */
  ResolvedTool resolve(String capability, ToolScope scope);

  /**
   * Lists the descriptors available within the given scope.
   *
   * @param scope workflow/run scope
   *
   * @return descriptors available within {@code scope}
   */
  List<ToolDescriptor> available(ToolScope scope);
}
