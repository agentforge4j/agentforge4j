// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * A capability resolved to the provider that fulfils it together with its descriptor.
 *
 * @param provider   non-null provider able to invoke the capability
 * @param descriptor non-null descriptor of the resolved tool
 */
public record ResolvedTool(ToolProvider provider, ToolDescriptor descriptor) {

  /**
   * Validates that {@code provider} and {@code descriptor} are non-null.
   */
  public ResolvedTool {
    Validate.notNull(provider, "ResolvedTool provider must not be null");
    Validate.notNull(descriptor, "ResolvedTool descriptor must not be null");
  }
}
