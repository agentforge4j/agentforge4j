// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;

/**
 * Default OSS {@link ToolPolicy} that allows every invocation. Tier gating, RBAC, and rate limits
 * are policy implementations injected by the embedding application, not core concepts.
 */
public final class NoOpToolPolicy implements ToolPolicy {

  @Override
  public PolicyDecision evaluate(ToolInvocationCommand cmd, ToolDescriptor descriptor,
      ToolInvocationContext ctx) {
    return new PolicyDecision.Allow();
  }
}
