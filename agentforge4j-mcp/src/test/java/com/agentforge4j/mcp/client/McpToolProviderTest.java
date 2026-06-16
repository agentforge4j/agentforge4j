package com.agentforge4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolScope;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.mcp.client.transport.McpTransport;
import com.agentforge4j.mcp.client.transport.RemoteTool;
import com.agentforge4j.mcp.client.transport.RemoteToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolProviderTest {

  private final ScriptedTransport transport = new ScriptedTransport();
  private final McpServerConnection connection =
      new McpServerConnection("mcp:test", transport);
  private final McpToolProvider provider = new McpToolProvider("mcp:test", connection);

  private final ToolInvocationContext context =
      new ToolInvocationContext("run-1", "1", "agent-1", new ToolScope("wf-1", "run-1"));

  @Test
  void listToolsMapsRemoteToolsToDescriptors() {
    transport.tools = List.of(
        new RemoteTool("create_pull_request", "Creates a PR", "{\"type\":\"object\"}"));

    List<ToolDescriptor> descriptors = provider.listTools();

    assertThat(descriptors).hasSize(1);
    ToolDescriptor descriptor = descriptors.get(0);
    assertThat(descriptor.capability()).isEqualTo("create_pull_request");
    assertThat(descriptor.description()).isEqualTo("Creates a PR");
    assertThat(descriptor.inputSchema()).isEqualTo("{\"type\":\"object\"}");
    assertThat(descriptor.source().providerId()).isEqualTo("mcp:test");
    assertThat(descriptor.source().remoteToolName()).isEqualTo("create_pull_request");
    // The MCP transport surfaces no mutation hint, so every realised tool defaults to conservative.
    assertThat(descriptor.riskMetadata().mutating()).isTrue();
    assertThat(transport.started).isTrue();
  }

  @Test
  void invokeSuccessProducesSuccessResult() {
    transport.result = RemoteToolResult.success("{\"url\":\"http://pr/1\"}");

    ToolResult result = provider.invoke(descriptorFor("create_pull_request"), "{\"title\":\"x\"}",
        context, ToolExecutionOptions.defaults());

    assertThat(result.success()).isTrue();
    assertThat(result.output()).isEqualTo("{\"url\":\"http://pr/1\"}");
    assertThat(result.latencyMillis()).isGreaterThanOrEqualTo(0L);
    assertThat(transport.lastToolName).isEqualTo("create_pull_request");
    assertThat(transport.lastArguments).isEqualTo("{\"title\":\"x\"}");
  }

  @Test
  void invokeErrorResultProducesFailureResult() {
    transport.result = RemoteToolResult.error("remote boom");

    ToolResult result = provider.invoke(descriptorFor("create_pull_request"), null,
        context, ToolExecutionOptions.defaults());

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage()).isEqualTo("remote boom");
    assertThat(result.output()).isNull();
  }

  @Test
  void healthReflectsConnectionState() {
    assertThat(provider.health().state()).isEqualTo(HealthStatus.State.DOWN);

    provider.listTools();

    assertThat(provider.health().state()).isEqualTo(HealthStatus.State.UP);
  }

  private static ToolDescriptor descriptorFor(String remoteToolName) {
    return new ToolDescriptor(remoteToolName, remoteToolName, null, null, null,
        new ToolSource("mcp:test", remoteToolName), ToolRiskMetadata.conservative());
  }

  private static final class ScriptedTransport implements McpTransport {

    private boolean started;
    private List<RemoteTool> tools = List.of();
    private RemoteToolResult result = RemoteToolResult.success(null);
    private String lastToolName;
    private String lastArguments;

    @Override
    public void start() {
      started = true;
    }

    @Override
    public List<RemoteTool> listTools() {
      return tools;
    }

    @Override
    public RemoteToolResult callTool(String remoteToolName, String argumentsJson) {
      this.lastToolName = remoteToolName;
      this.lastArguments = argumentsJson;
      return result;
    }

    @Override
    public boolean isReady() {
      return started;
    }

    @Override
    public void close() {
      started = false;
    }
  }
}
