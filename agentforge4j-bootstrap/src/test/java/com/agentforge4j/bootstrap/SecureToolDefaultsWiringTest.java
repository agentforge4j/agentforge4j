// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.command.ToolInvocationCommand;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ResolvedTool;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolExecutionOutcome;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.core.spi.tool.ToolSourceKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the secure tool defaults wired by {@link AgentForge4jBootstrap}: the default
 * {@code ToolPolicy} must be the fail-closed {@code SecureDefaultToolPolicy}, an explicit
 * {@link ToolPolicy#allowAll()} must lift it, and {@code withAllowPrivateNetworks(true)} must thread
 * through to the {@code HttpEgressGuard} on the discovered HTTP tool path. These assert the
 * <em>bootstrap wiring</em>; the policy and guard themselves are unit-tested in their own modules.
 */
class SecureToolDefaultsWiringTest {

  private static final String CAPABILITY = "demo.do_thing";

  private final ToolInvocationContext ctx =
      new ToolInvocationContext("run-1", "1", "agent-1", new ToolScope("wf-1", "run-1"));

  @Test
  void defaultPolicyDeniesRemoteHttpToolsWithoutAnExplicitOptIn() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviders(List.of(provider("http:remote", ToolSourceKind.REMOTE_HTTP)))
        .build();

    ToolExecutionOutcome outcome = af.components().toolExecutionService()
        .execute(new ToolInvocationCommand(null, CAPABILITY, Map.of(), "because"), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.DENIED);
  }

  @Test
  void defaultPolicyAllowsInProcessTools() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviders(List.of(provider("inproc:demo", ToolSourceKind.IN_PROCESS)))
        .build();

    ToolExecutionOutcome outcome = af.components().toolExecutionService()
        .execute(new ToolInvocationCommand(null, CAPABILITY, Map.of(), "because"), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
  }

  @Test
  void allowAllPolicyLiftsTheDenyForRemoteHttpTools() {
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withToolProviders(List.of(provider("http:remote", ToolSourceKind.REMOTE_HTTP)))
        .withToolPolicy(ToolPolicy.allowAll())
        .build();

    ToolExecutionOutcome outcome = af.components().toolExecutionService()
        .execute(new ToolInvocationCommand(null, CAPABILITY, Map.of(), "because"), ctx);

    assertThat(outcome.status()).isEqualTo(ToolExecutionOutcome.Status.EXECUTED);
  }

  @Test
  void egressGuardBlocksAPrivateTargetByDefault() {
    // Default safety: a default build sets NO private-network opt-out, so the egress guard the
    // bootstrap wires into the discovered HTTP tool factory must block a private/loopback target
    // before any connection. (The policy gate is independent and covered above.) A blocked target is
    // inherent to a default-deny assertion — a public URI cannot exercise it without a real external
    // call, so this uses the loopback stub address purely as a guaranteed-blocked target.
    ToolResult result = invokeDiscoveredHttpTool(AgentForge4jBootstrap.defaults()
        .withIntegrationConfigLoader(() -> List.of(loopbackHttpIntegration()))
        .build());

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).contains("127.0.0.1", "non-public");
  }

  @Test
  void devOnlyAllowPrivateNetworksOptOutLiftsTheEgressGuard() {
    // Explicitly exercises the DEVELOPMENT-ONLY broad escape hatch — not a recommended pattern and
    // not how an example should reach a local stub. It proves only that withAllowPrivateNetworks(true)
    // threads from the builder into the guard: the same private target is no longer egress-blocked,
    // so the failure becomes a connection error (closed port 1) rather than an egress block.
    ToolResult result = invokeDiscoveredHttpTool(AgentForge4jBootstrap.defaults()
        .withIntegrationConfigLoader(() -> List.of(loopbackHttpIntegration()))
        .withAllowPrivateNetworks(true)
        .build());

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).doesNotContain("non-public");
  }

  private ToolResult invokeDiscoveredHttpTool(AgentForge4j af) {
    ResolvedTool resolved = af.components().toolProviderResolver()
        .resolve("egress.probe", new ToolScope("wf-1", "run-1"));
    return resolved.provider()
        .invoke(resolved.descriptor(), "{}", ctx, ToolExecutionOptions.defaults());
  }

  private static IntegrationDefinition loopbackHttpIntegration() {
    String config = """
        [
          {
            "capability": "egress.probe",
            "method": "GET",
            "urlTemplate": "http://127.0.0.1:1/",
            "inputSchema": { "type": "object", "additionalProperties": true },
            "bodyMode": "NONE"
          }
        ]
        """;
    return new IntegrationDefinition("egress-probe", "Egress probe", IntegrationType.HTTP_TOOL,
        config, true);
  }

  private static ToolProvider provider(String providerId, ToolSourceKind kind) {
    return new ToolProvider() {
      @Override
      public String providerId() {
        return providerId;
      }

      @Override
      public List<ToolDescriptor> listTools() {
        return List.of(new ToolDescriptor(CAPABILITY, CAPABILITY, null, null, null,
            new ToolSource(providerId, "do_thing", kind), ToolRiskMetadata.conservative()));
      }

      @Override
      public ToolResult invoke(ToolDescriptor descriptor, String arguments,
          ToolInvocationContext invocationContext, ToolExecutionOptions options) {
        return ToolResult.success("{\"ok\":true}", 1L);
      }

      @Override
      public HealthStatus health() {
        return new HealthStatus(HealthStatus.State.UP, null);
      }
    };
  }
}
