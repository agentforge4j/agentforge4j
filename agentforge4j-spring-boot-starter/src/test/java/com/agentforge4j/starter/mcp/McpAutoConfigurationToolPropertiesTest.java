// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.mcp.client.McpServerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Guards that {@link McpAutoConfiguration} owns the {@code agentforge4j.tools.*} binding it consumes.
 * {@code mcpServerRegistry} reads {@link ToolProperties} to build the egress guard, so MCP
 * auto-configuration must declare {@link EnableConfigurationProperties} for it and resolve it even
 * when activated without {@code BootstrapAutoConfiguration}.
 */
class McpAutoConfigurationToolPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class));

  @Test
  void declaresToolPropertiesAmongItsConfigurationProperties() {
    EnableConfigurationProperties annotation =
        McpAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains(ToolProperties.class);
  }

  @Test
  void resolvesToolPropertiesAndMcpRegistryStandalone() {
    runner.run(context -> {
      assertThat(context.getStartupFailure()).isNull();
      assertThat(context).hasSingleBean(ToolProperties.class);
      assertThat(context).hasSingleBean(McpServerRegistry.class);
    });
  }
}
