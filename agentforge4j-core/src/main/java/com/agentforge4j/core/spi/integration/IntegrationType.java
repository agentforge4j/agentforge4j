package com.agentforge4j.core.spi.integration;

/**
 * The kind of external integration an {@link IntegrationDefinition} describes.
 */
public enum IntegrationType {

  /**
   * An MCP server launched as a local subprocess and spoken to over stdio.
   */
  MCP_STDIO,

  /**
   * A hosted/remote MCP server reached over Streamable HTTP.
   */
  MCP_STREAMABLE_HTTP,

  /**
   * A code-defined set of governed HTTP endpoints (the HTTP tool tier).
   */
  HTTP_TOOL
}
