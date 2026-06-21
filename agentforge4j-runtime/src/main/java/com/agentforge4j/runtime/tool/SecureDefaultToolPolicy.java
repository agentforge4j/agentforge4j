// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;

/**
 * Secure-by-default OSS {@link ToolPolicy}: allows in-process tools (the embedder's own registered code) and denies
 * remote-network and local-process tools, which require an explicit trust decision ({@link ToolPolicy#allowAll()} or a
 * custom policy). The decision is made solely on the framework-trusted
 * {@link com.agentforge4j.core.spi.tool.ToolSourceKind}; it does not consult provider-declared
 * {@link com.agentforge4j.core.spi.tool.ToolRiskMetadata}, which is untrusted advisory metadata and plays no part in
 * this default policy's allow decision.
 *
 * <p>Dangerous classes are <em>denied</em>, not suspended for approval, so an embedder that has not
 * wired an approval handler is never stranded.
 */
public final class SecureDefaultToolPolicy implements ToolPolicy {

  @Override
  public PolicyDecision evaluate(ToolInvocationCommand cmd, ToolDescriptor descriptor,
      ToolInvocationContext ctx) {
    return switch (descriptor.source().kind()) {
      case IN_PROCESS -> new PolicyDecision.Allow();
      case REMOTE_HTTP -> new PolicyDecision.Deny(
          ("remote network tool '%s' is denied by the secure default policy; supply "
              + "ToolPolicy.allowAll() or a custom policy to allow it")
              .formatted(descriptor.source().providerId()));
      case LOCAL_PROCESS -> new PolicyDecision.Deny(
          ("local-process tool '%s' is denied by the secure default policy; supply "
              + "ToolPolicy.allowAll() or a custom policy to allow it")
              .formatted(descriptor.source().providerId()));
    };
  }
}
