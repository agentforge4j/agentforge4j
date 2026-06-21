// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

/**
 * Structural classification of where a tool physically executes. Set by the framework's provider factory at descriptor
 * construction, so it is a <em>framework-trusted</em> fact — unlike provider-declared {@link ToolRiskMetadata}, which
 * is untrusted advisory metadata. A {@link ToolPolicy} may therefore key its positive allow decision on this kind,
 * because it is established by OSS code, not by a remote provider's self-description.
 */
public enum ToolSourceKind {

  /**
   * In-process tool registered by the embedding application's own code: no external network egress and no process
   * launch.
   */
  IN_PROCESS,

  /**
   * Remote tool reached over the network — the HTTP tool provider, or MCP over streamable HTTP.
   */
  REMOTE_HTTP,

  /**
   * Tool backed by a launched local subprocess — MCP over stdio.
   */
  LOCAL_PROCESS
}
