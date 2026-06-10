package com.agentforge4j.mcp.client.transport;

import com.agentforge4j.util.Validate;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@link McpTransport} that launches a local MCP server subprocess over stdio.
 *
 * <p>The command, arguments, and environment are passed verbatim to the subprocess launcher and
 * must come from trusted configuration only — never from request-time or model-supplied input.
 */
public final class StdioTransport extends AbstractSdkMcpTransport {

  private final String command;
  private final List<String> args;
  private final Map<String, String> env;

  /**
   * Creates a stdio transport.
   *
   * @param command        the executable to launch (non-blank)
   * @param args           command-line arguments, or {@code null} for none
   * @param env            environment variables, or {@code null} for none
   * @param requestTimeout per-request timeout
   * @param jsonMapper     the JSON mapper used by the SDK transport
   */
  public StdioTransport(String command, List<String> args, Map<String, String> env,
      Duration requestTimeout, McpJsonMapper jsonMapper) {
    super(jsonMapper, requestTimeout);
    this.command = Validate.notBlank(command, "command must not be blank");
    this.args = args != null ? List.copyOf(args) : List.of();
    this.env = env != null ? Map.copyOf(env) : Map.of();
  }

  @Override
  protected McpClientTransport createSdkTransport() {
    ServerParameters parameters = ServerParameters.builder(command)
        .args(args)
        .env(env)
        .build();
    return new StdioClientTransport(parameters, jsonMapper());
  }
}
