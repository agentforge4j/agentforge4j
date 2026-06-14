package com.agentforge4j.core.spi.tool;

import com.agentforge4j.core.command.ToolInvocationCommand;

/**
 * Policy gate for tool invocation. Tier gating, RBAC, allowlists, and rate limits are policy
 * implementations injected by the embedding application, not core concepts. The OSS default is
 * {@code NoOpToolPolicy} returning {@link PolicyDecision.Allow}.
 */
public interface ToolPolicy {

  /**
   * Evaluates a requested tool invocation. Argument-schema validation runs in the execution service
   * before this is consulted, so implementations receive arguments already shape-checked against
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
}
