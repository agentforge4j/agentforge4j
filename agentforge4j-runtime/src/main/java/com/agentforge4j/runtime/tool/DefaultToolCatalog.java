// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.spi.tool.ToolCatalog;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolProviderResolver;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * {@link ToolCatalog} that delegates to the {@link ToolProviderResolver}, so the advertised catalog
 * and the resolver can never drift. An empty resolver yields an empty catalog.
 */
public final class DefaultToolCatalog implements ToolCatalog {

  private final ToolProviderResolver resolver;

  /**
   * Creates a catalog backed by the given resolver.
   *
   * @param resolver the resolver backing this catalog
   */
  public DefaultToolCatalog(ToolProviderResolver resolver) {
    this.resolver = Validate.notNull(resolver, "resolver must not be null");
  }

  @Override
  public List<ToolDescriptor> available(ToolScope scope) {
    return resolver.available(scope);
  }
}
