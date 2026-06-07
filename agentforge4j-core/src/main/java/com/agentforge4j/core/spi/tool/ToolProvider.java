package com.agentforge4j.core.spi.tool;

import java.util.List;

/**
 * A source of invocable tools, for example an MCP server. Implementations: {@code McpToolProvider}
 * (Phase 1b); future built-in and HTTP providers.
 */
public interface ToolProvider {

  /**
   * @return stable, non-blank provider id, for example {@code "mcp:github-official"}
   */
  String providerId();

  /**
   * @return descriptors for every tool this provider currently exposes
   */
  List<ToolDescriptor> listTools();

  /**
   * Invokes the resolved tool.
   *
   * @param descriptor resolved descriptor; use {@link ToolSource#remoteToolName()} for the remote
   *                   name
   * @param arguments  tool arguments as JSON text, or {@code null}
   * @param ctx        invocation context
   * @param options    tunables; the provider may honour these best-effort, but the authoritative
   *                   timeout is enforced by the execution service
   *
   * @return the invocation result
   */
  ToolResult invoke(ToolDescriptor descriptor, String arguments, ToolInvocationContext ctx,
      ToolExecutionOptions options);

  /**
   * @return current provider health
   */
  HealthStatus health();
}
