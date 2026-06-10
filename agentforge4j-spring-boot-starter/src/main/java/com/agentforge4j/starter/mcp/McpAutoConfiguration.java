package com.agentforge4j.starter.mcp;

import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.mcp.client.McpServerConnection;
import com.agentforge4j.mcp.client.McpServerRegistry;
import com.agentforge4j.mcp.client.McpToolProvider;
import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.mcp.client.transport.StdioTransport;
import com.agentforge4j.mcp.client.transport.StreamableHttpTransport;
import com.agentforge4j.runtime.tool.NoOpToolPolicy;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import com.agentforge4j.util.Validate;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires MCP servers from {@code agentforge4j.mcp.*} into the runtime's tool SPI, and tool tunables
 * from {@code agentforge4j.tools.*}. Active only when the {@code agentforge4j-mcp} module is on the
 * classpath; the exposed {@link ToolProvider}, {@link ToolPolicy}, and {@link ToolExecutionOptions}
 * beans are consumed by {@link BootstrapAutoConfiguration}.
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@ConditionalOnClass(McpToolProvider.class)
@EnableConfigurationProperties({McpProperties.class, ToolProperties.class})
public class McpAutoConfiguration {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /**
   * @param properties bound MCP properties
   *
   * @return a registry of connections for every enabled configured server
   */
  @Bean
  @ConditionalOnMissingBean
  public McpServerRegistry mcpServerRegistry(McpProperties properties) {
    McpServerRegistry registry = new McpServerRegistry();
    for (McpProperties.ServerProperties server : enabledServers(properties)) {
      registry.register(new McpServerConnection(server.id(), buildTransport(server)));
    }
    return registry;
  }

  /**
   * Exposes one {@link McpToolProvider} per enabled configured server, wired with the corresponding
   * connection from the registry. Each provider's ID is derived from the server configuration: if
   * {@code providerId} is set, it is used directly; otherwise, the provider ID defaults to
   * {@code "mcp:" + serverId}. The provider ID is what agents use in their
   * {@code providerPreferences} to select this tool provider for MCP tool execution. The provider's
   * connection is looked up from the registry by the server ID, so the registry bean must be
   * initialized first. The returned providers are collected into a list and exposed as a single
   * bean for the runtime to consume.
   *
   * @param registry   the MCP server registry
   * @param properties bound MCP properties
   *
   * @return one {@link McpToolProvider} per enabled configured server
   */
  @Bean
  public List<ToolProvider> mcpToolProviders(McpServerRegistry registry, McpProperties properties) {
    List<ToolProvider> providers = new ArrayList<>();
    for (McpProperties.ServerProperties server : enabledServers(properties)) {
      McpServerConnection connection = registry.connection(server.id());
      providers.add(new McpToolProvider(providerId(server), connection));
    }
    return providers;
  }

  /**
   * @return the default no-op tool policy when the application provides none
   */
  @Bean
  @ConditionalOnMissingBean(ToolPolicy.class)
  public ToolPolicy noOpToolPolicy() {
    return new NoOpToolPolicy();
  }

  /**
   * @param properties bound tool properties
   *
   * @return the tool execution options derived from configuration
   */
  @Bean
  @ConditionalOnMissingBean(ToolExecutionOptions.class)
  public ToolExecutionOptions toolExecutionOptions(ToolProperties properties) {
    return new ToolExecutionOptions(
        ObjectUtils.getIfNull(properties.timeout(), DEFAULT_TIMEOUT),
        ObjectUtils.getIfNull(properties.maxRetries(), 0),
        ObjectUtils.getIfNull(properties.retryBackoff(), Duration.ZERO));
  }

  private static List<McpProperties.ServerProperties> enabledServers(McpProperties properties) {
    if (properties == null || properties.servers() == null) {
      return List.of();
    }
    List<McpProperties.ServerProperties> enabled = new ArrayList<>();
    for (McpProperties.ServerProperties server : properties.servers()) {
      Validate.notBlank(server.id(), "MCP server id must not be blank");
      if (!Boolean.FALSE.equals(server.enabled())) {
        enabled.add(server);
      }
    }
    return enabled;
  }

  private static McpTransport buildTransport(McpProperties.ServerProperties server) {
    Duration timeout = ObjectUtils.getIfNull(server.requestTimeout(), DEFAULT_TIMEOUT);
    McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
    if ("STREAMABLE_HTTP".equalsIgnoreCase(server.transport())) {
      Validate.notBlank(server.url(),
          "MCP server '%s' with STREAMABLE_HTTP transport requires a url".formatted(server.id()));
      return new StreamableHttpTransport(server.url(), timeout, server.headers(), Map.of(), null,
          jsonMapper);
    }
    Validate.notBlank(server.command(),
        "MCP server '%s' with STDIO transport requires a command".formatted(server.id()));
    return new StdioTransport(server.command(), server.args(), server.env(), timeout, jsonMapper);
  }

  private static String providerId(McpProperties.ServerProperties server) {
    return StringUtils.defaultIfBlank(server.providerId(), "mcp:" + server.id());
  }
}
