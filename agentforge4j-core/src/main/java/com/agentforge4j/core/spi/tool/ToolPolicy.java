// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;

/**
 * Policy gate for tool invocation. Tier gating, RBAC, allowlists, and rate limits are policy implementations injected
 * by the embedding application, not core concepts. The OSS default is {@code SecureDefaultToolPolicy}, which allows
 * in-process tools and denies remote-network and local-process tools unless an explicit policy ({@link #allowAll()} or
 * a custom one) opts in.
 */
public interface ToolPolicy {

  /**
   * Evaluates a requested tool invocation. Argument-schema validation runs in the execution service before this is
   * consulted, so implementations receive arguments already shape-checked against
   * {@link ToolDescriptor#inputSchema()}.
   *
   * @param cmd        the requested invocation
   * @param descriptor the resolved tool descriptor
   * @param ctx        invocation context
   *
   * @return the policy decision
   */
  PolicyDecision evaluate(ToolInvocationCommand cmd, ToolDescriptor descriptor,
      ToolInvocationContext ctx);

  /**
   * Returns a policy that allows every invocation — the explicit, trusted opt-in to the pre-secure-default behaviour.
   * Use only when the embedding application fully trusts every registered tool (for example demos, or a host that
   * applies its own out-of-band controls). The framework default is {@code SecureDefaultToolPolicy}, which denies
   * remote-network and local-process tools unless an explicit policy allows them.
   *
   * @return an allow-all policy
   */
  static ToolPolicy allowAll() {
    return (cmd, descriptor, ctx) -> new PolicyDecision.Allow();
  }
}
