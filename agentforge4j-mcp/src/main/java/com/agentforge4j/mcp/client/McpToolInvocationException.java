package com.agentforge4j.mcp.client;

/**
 * Thrown when an MCP tool listing or invocation fails for reasons other than a tool-reported error
 * (for example serialization or transport failures).
 */
public class McpToolInvocationException extends RuntimeException {

  /**
   * Creates the exception with a detail message.
   *
   * @param message the detail message
   */
  public McpToolInvocationException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a detail message and underlying cause.
   *
   * @param message the detail message
   * @param cause   the underlying cause
   */
  public McpToolInvocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
