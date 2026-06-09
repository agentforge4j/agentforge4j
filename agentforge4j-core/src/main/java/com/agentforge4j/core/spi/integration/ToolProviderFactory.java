package com.agentforge4j.core.spi.integration;

import com.agentforge4j.core.spi.tool.ToolProvider;

/**
 * Creates the runtime {@link ToolProvider} for an {@link IntegrationDefinition}, interpreting its
 * {@link IntegrationDefinition#type()} and {@code config}.
 *
 * <p>Pure contract; the implementation lives downstream where the concrete providers
 * ({@code McpToolProvider}, {@code HttpToolProvider}) are on the classpath.
 */
public interface ToolProviderFactory {

  /**
   * Builds the tool provider for an integration.
   *
   * @param definition the integration to realise
   *
   * @return a tool provider exposing the integration's capabilities
   */
  ToolProvider create(IntegrationDefinition definition);
}
