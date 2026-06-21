// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import java.util.List;

/**
 * A source of invocable tools, for example an MCP server. Implementations: {@code McpToolProvider} (Phase 1b); future
 * built-in and HTTP providers.
 *
 * <p><strong>Trust note.</strong> A provider supplied directly to the bootstrap (rather than realised
 * from an integration definition by a framework factory) is treated as trusted embedder code. The
 * {@link ToolSourceKind} its descriptors declare via {@link ToolSource} is taken at face value by the secure default
 * {@code ToolPolicy} — so an embedder-supplied provider that declares {@link ToolSourceKind#IN_PROCESS} is allowed by
 * default. Declare the kind that honestly reflects where the tool executes; for anything not under the embedder's own
 * control, gate it with a custom {@code ToolPolicy} rather than relying on the declared kind.
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
   * @param descriptor resolved descriptor; use {@link ToolSource#remoteToolName()} for the remote name
   * @param arguments  tool arguments as JSON text, or {@code null}
   * @param ctx        invocation context
   * @param options    tunables; the provider may honour these best-effort, but the authoritative timeout is enforced by
   *                   the execution service
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
