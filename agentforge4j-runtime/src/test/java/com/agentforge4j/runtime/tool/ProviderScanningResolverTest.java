package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderScanningResolverTest {

  private final ToolScope scope = new ToolScope("wf", "run");

  @Test
  void resolvesUniqueCapability() {
    ToolProvider provider = provider("mcp:a", "github.create_pull_request");
    ProviderScanningResolver resolver = new ProviderScanningResolver(List.of(provider));

    ResolvedTool resolved = resolver.resolve("github.create_pull_request", scope);

    assertThat(resolved.provider()).isSameAs(provider);
    assertThat(resolved.descriptor().capability()).isEqualTo("github.create_pull_request");
  }

  @Test
  void unknownCapabilityFailsFast() {
    ProviderScanningResolver resolver =
        new ProviderScanningResolver(List.of(provider("mcp:a", "github.create_pull_request")));

    assertThatThrownBy(() -> resolver.resolve("jira.create_issue", scope))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("jira.create_issue");
  }

  @Test
  void ambiguousCapabilityFailsFastAtConstructionNamingBothProviders() {
    assertThatThrownBy(() -> new ProviderScanningResolver(List.of(
        provider("mcp:a", "github.create_pull_request"),
        provider("mcp:b", "github.create_pull_request"))))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("mcp:a")
        .hasMessageContaining("mcp:b");
  }

  @Test
  void availableAggregatesAllDescriptors() {
    ProviderScanningResolver resolver = new ProviderScanningResolver(List.of(
        provider("mcp:a", "github.create_pull_request"),
        provider("mcp:b", "jira.create_issue")));

    assertThat(resolver.available(scope))
        .extracting(ToolDescriptor::capability)
        .containsExactlyInAnyOrder("github.create_pull_request", "jira.create_issue");
  }

  private static ToolProvider provider(String providerId, String capability) {
    return new ToolProvider() {
      @Override
      public String providerId() {
        return providerId;
      }

      @Override
      public List<ToolDescriptor> listTools() {
        return List.of(new ToolDescriptor(capability, capability, null, null, null,
            new ToolSource(providerId, capability)));
      }

      @Override
      public ToolResult invoke(ToolDescriptor descriptor, String arguments,
          ToolInvocationContext ctx, ToolExecutionOptions options) {
        return ToolResult.success(null, 0L);
      }

      @Override
      public HealthStatus health() {
        return new HealthStatus(HealthStatus.State.UP, null);
      }
    };
  }
}
