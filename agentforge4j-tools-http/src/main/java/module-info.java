/**
 * HTTP tool provider: exposes governed external HTTP endpoints as tool-SPI {@code ToolProvider}s.
 *
 * <p>Code-defined only: a consumer supplies {@code HttpEndpointDefinition}s and a secret resolver,
 * and the provider maps a capability invocation to a single HTTP call. Depends on
 * {@code agentforge4j.core} (the tool SPI) and the JDK HTTP client only; it carries no platform,
 * tenant, or LLM concerns and is wired through the bootstrap tool-provider escape hatch.
 */
module agentforge4j.tools.http {
  requires agentforge4j.core;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires org.apache.commons.lang3;

  exports com.agentforge4j.tools.http;
}
