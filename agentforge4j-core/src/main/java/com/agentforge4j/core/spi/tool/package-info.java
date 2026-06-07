/**
 * Transport-agnostic SPI contracts for governed tool invocation (MCP and future tool sources).
 *
 * <p>The LLM may <em>request</em> a tool call; only the runtime may approve, execute, audit, retry,
 * or reject
 * it. All contracts here are pure: no Spring, no persistence, no LLM-vendor, and no
 * tenant/user/role concept (identity lives only in platform implementations). Implementations live
 * downstream — the runtime owns {@code DefaultToolExecutionService}, the {@code agentforge4j-mcp}
 * module owns {@code McpToolProvider}, and the platform owns persistence-backed resolvers and
 * policies.
 */
package com.agentforge4j.core.spi.tool;
