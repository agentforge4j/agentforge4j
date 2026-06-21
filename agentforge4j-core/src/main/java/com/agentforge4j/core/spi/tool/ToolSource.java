// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * Separates a logical capability from the physical remote tool that fulfils it.
 *
 * @param providerId     id of the provider, for example {@code "mcp:github-official"}
 * @param remoteToolName the tool's name on the remote MCP server
 * @param kind           structural classification of where the tool executes; framework-trusted, set by the provider
 *                       factory (see {@link ToolSourceKind})
 */
public record ToolSource(String providerId, String remoteToolName, ToolSourceKind kind) {

  /**
   * Validates that {@code providerId} and {@code remoteToolName} are non-blank and {@code kind} is non-null.
   */
  public ToolSource {
    Validate.notBlank(providerId, "ToolSource providerId must not be blank");
    Validate.notBlank(remoteToolName, "ToolSource remoteToolName must not be blank");
    Validate.notNull(kind, "ToolSource kind must not be null");
  }
}
