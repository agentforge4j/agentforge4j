/**
 * HTTP tool provider: exposes governed external HTTP endpoints as tool-SPI {@code ToolProvider}s.
 *
 * <p>A consumer supplies {@code HttpEndpointDefinition}s and a secret resolver, and the provider
 * maps a capability invocation to a single HTTP call. Two wiring paths are supported: code-defined via the bootstrap
 * {@code withToolProviders(...)} escape hatch, and config-loaded via the ServiceLoader-discovered
 * {@code HttpToolProviderFactory} contribution (realising {@code HTTP_TOOL} integration definitions). Depends on
 * {@code agentforge4j.core} (the tool and integration SPIs) and the JDK HTTP client only; it carries no persistence,
 * tenant, or LLM concerns.
 */
module agentforge4j.tools.http {
  requires agentforge4j.core;
  requires agentforge4j.util;
  requires com.fasterxml.jackson.databind;
  requires java.net.http;
  requires org.apache.commons.lang3;

  exports com.agentforge4j.tools.http;

  provides com.agentforge4j.core.spi.integration.IntegrationToolProviderFactory
      with com.agentforge4j.tools.http.HttpToolProviderFactory;
}
