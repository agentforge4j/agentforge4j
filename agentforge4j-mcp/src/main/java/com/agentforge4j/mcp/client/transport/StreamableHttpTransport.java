package com.agentforge4j.mcp.client.transport;

import com.agentforge4j.util.Validate;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.time.Duration;

/**
 * {@link McpTransport} that connects to a remote MCP server over Streamable HTTP.
 */
public final class StreamableHttpTransport extends AbstractSdkMcpTransport {

  private final String url;

  /**
   * Creates a Streamable HTTP transport using the SDK's default Jackson-2 JSON mapper.
   *
   * @param url            the base URL of the remote MCP server (non-blank)
   * @param requestTimeout per-request timeout
   */
  public StreamableHttpTransport(String url, Duration requestTimeout) {
    this(url, requestTimeout, defaultJsonMapper());
  }

  /**
   * Creates a Streamable HTTP transport with an explicit JSON mapper.
   *
   * @param url            the base URL of the remote MCP server (non-blank)
   * @param requestTimeout per-request timeout
   * @param jsonMapper     the JSON mapper used by the SDK transport
   */
  public StreamableHttpTransport(String url, Duration requestTimeout, McpJsonMapper jsonMapper) {
    super(jsonMapper, requestTimeout);
    this.url = Validate.notBlank(url, "url must not be blank");
  }

  @Override
  protected McpClientTransport createSdkTransport() {
    return HttpClientStreamableHttpTransport.builder(url)
        .jsonMapper(jsonMapper())
        .build();
  }
}
