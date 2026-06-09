/**
 * Transport-agnostic SPI contracts for declaring external integrations (MCP servers and HTTP tool
 * sets) and turning them into governed tool providers.
 *
 * <p>An {@link com.agentforge4j.core.spi.integration.IntegrationDefinition} maps one external
 * service to the capabilities it exposes; an
 * {@link com.agentforge4j.core.spi.integration.IntegrationRepository} is the single source of
 * integrations feeding capability resolution; an
 * {@link com.agentforge4j.core.spi.integration.IntegrationConfigLoader} loads definitions; and an
 * {@link com.agentforge4j.core.spi.integration.ToolProviderFactory} realises each definition as a
 * {@link com.agentforge4j.core.spi.tool.ToolProvider}.
 *
 * <p>All contracts here are pure: no Spring, persistence, LLM-vendor, or tenant/user/role concept.
 * Implementations live downstream (OSS loader/in-memory; platform persistence-backed).
 */
package com.agentforge4j.core.spi.integration;
