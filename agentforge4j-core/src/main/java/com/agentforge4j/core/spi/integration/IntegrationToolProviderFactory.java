// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.integration;

import com.agentforge4j.core.spi.tool.ToolProvider;

/**
 * Per-type contribution to {@link ToolProviderFactory}: realises integrations of exactly one
 * {@link IntegrationType} as runtime {@link ToolProvider}s.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} (mirroring
 * {@code LlmClientFactory}) and live in the provider module that has the concrete provider on the
 * classpath — for example {@code agentforge4j-mcp} contributes the MCP types. The aggregating
 * {@link ToolProviderFactory} routes each definition to the contribution matching its type.
 */
public interface IntegrationToolProviderFactory {

  /**
   * Returns the single integration type this contribution realises.
   *
   * @return the supported type, never {@code null}
   */
  IntegrationType supportedType();

  /**
   * Builds the tool provider for an integration of the
   * {@linkplain #supportedType() supported type}, interpreting its type-specific {@code config}
   * JSON with the collaborators supplied by {@code context} (for example the shared Jackson
   * mapper).
   *
   * @param definition the integration to realise; its type must equal {@link #supportedType()}
   * @param context    framework-supplied collaborators; never {@code null}
   *
   * @return a tool provider exposing the integration's capabilities
   *
   * @throws IllegalArgumentException if the definition's type does not match or its {@code config}
   *                                  payload is malformed or incomplete
   */
  ToolProvider create(IntegrationDefinition definition, ToolProviderFactoryContext context);
}
