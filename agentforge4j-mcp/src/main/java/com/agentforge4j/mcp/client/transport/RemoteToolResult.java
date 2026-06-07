package com.agentforge4j.mcp.client.transport;

/**
 * Transport-neutral result of a remote tool call, decoupled from the MCP SDK types.
 *
 * @param error        whether the remote server reported a tool error
 * @param output       tool output as JSON or text; {@code null} on error
 * @param errorMessage error detail; {@code null} on success
 */
public record RemoteToolResult(boolean error, String output, String errorMessage) {

  /**
   * Creates a success result.
   *
   * @param output the tool output
   *
   * @return a success result
   */
  public static RemoteToolResult success(String output) {
    return new RemoteToolResult(false, output, null);
  }

  /**
   * Creates an error result.
   *
   * @param errorMessage the error detail
   *
   * @return an error result
   */
  public static RemoteToolResult error(String errorMessage) {
    return new RemoteToolResult(true, null, errorMessage);
  }
}
