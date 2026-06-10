package com.agentforge4j.starter.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code agentforge4j.mcp.*} configuration: the MCP servers to connect to.
 *
 * @param servers configured MCP servers; {@code null} or empty disables MCP wiring
 */
@ConfigurationProperties(prefix = "agentforge4j.mcp")
public record McpProperties(List<ServerProperties> servers) {

  /**
   * One MCP server.
   *
   * @param id             unique server id (also the connection key)
   * @param providerId     logical provider id (defaults to {@code "mcp:<id>"} when blank)
   * @param transport      {@code STDIO} or {@code STREAMABLE_HTTP}; defaults to {@code STDIO}
   * @param command        executable for stdio transport
   * @param args           command-line arguments for stdio transport
   * @param url            base URL for streamable HTTP transport
   * @param env            environment variables for stdio transport
   * @param headers        literal request headers for streamable HTTP transport, sent on every
   *                       request (for example {@code Authorization} for hosted servers);
   *                       {@code null} means none
   * @param enabled        whether the server is active; {@code null} means enabled
   * @param requestTimeout per-request timeout; defaults to 30s when {@code null}
   */
  public record ServerProperties(
      String id,
      String providerId,
      String transport,
      String command,
      List<String> args,
      String url,
      Map<String, String> env,
      Map<String, String> headers,
      Boolean enabled,
      Duration requestTimeout) {

  }
}
