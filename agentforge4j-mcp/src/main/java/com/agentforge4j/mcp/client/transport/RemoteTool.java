// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.mcp.client.transport;

import com.agentforge4j.util.Validate;

/**
 * Transport-neutral description of a tool exposed by a remote MCP server, decoupled from the MCP
 * SDK types.
 *
 * @param name            the tool's name on the remote server
 * @param description     human-readable description, or {@code null}
 * @param inputSchemaJson the tool's JSON Schema for arguments as JSON text, or {@code null}
 */
public record RemoteTool(String name, String description, String inputSchemaJson) {

  /**
   * Validates that {@code name} is non-blank.
   */
  public RemoteTool {
    Validate.notBlank(name, "RemoteTool name must not be blank");
  }
}
