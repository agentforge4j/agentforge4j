// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.tool;

import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.tools.http.BodyMode;
import com.agentforge4j.tools.http.HttpEndpointDefinition;
import com.agentforge4j.tools.http.HttpMethod;
import com.agentforge4j.tools.http.HttpToolProvider;
import com.agentforge4j.util.net.HttpEgressGuard;
import com.agentforge4j.verification.support.Fixtures;
import com.agentforge4j.verification.support.LoopbackHttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * HTTP-tool governance verification: the equivalent of {@link McpToolGovernanceTest} for the
 * {@code HTTP_TOOL} provider. A real {@link HttpToolProvider} (real serialization, real egress
 * guard, real JDK HTTP client against a loopback stub) is resolved and invoked through the runtime
 * governance chokepoint end-to-end — proving the integration wiring, not just the provider unit
 * behaviour already covered in {@code agentforge4j-tools-http}.
 */
class HttpToolGovernanceTest {

  private static final String CAPABILITY = "test.echo";
  private static final String RESPONSE_BODY = "{\"ok\":true}";

  private static FakeScript script() {
    try {
      return new FakeScriptParser().parse(
          Files.readString(Fixtures.dir("/fixtures/tool/script.json")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read tool fake script", e);
    }
  }

  @Test
  void httpToolResolvesAndInvokesThroughTheGovernanceChokepoint() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (LoopbackHttpServer server = new LoopbackHttpServer(RESPONSE_BODY)) {
      HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
          .withCapability(CAPABILITY)
          .withDisplayName("Echo")
          .withMutating(false)
          .withMethod(HttpMethod.GET)
          .withUrlTemplate(server.baseUri() + "/echo")
          .withInputSchema(mapper.readTree(
              "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}}}"))
          .withQueryArgs(Set.of("message"))
          .withBodyMode(BodyMode.NONE)
          .build();
      ToolProvider provider = new HttpToolProvider("verification", List.of(definition),
          key -> null, HttpClient.newHttpClient(), new HttpEgressGuard(true),
          ToolExecutionOptions.defaults(), 1_048_576L, mapper);

      WorkflowRunResult result = WorkflowTestHarness.builder()
          .workflowsDir(Fixtures.dir("/fixtures/tool/workflows"))
          .agentsDir(Fixtures.dir("/fixtures/tool/agents"))
          .script(script())
          .toolProviders(List.of(provider))
          // HTTP tools are remote, so the secure default policy denies them; this test verifies
          // the chokepoint mechanics under an explicit allow — the deny default itself is covered
          // by SecureDefaultToolPolicyTest.
          .toolPolicy(ToolPolicy.allowAll())
          .build()
          .run("tool-run");

      WorkflowRunAssert.assertThat(result)
          .isCompleted()
          .invokedTool(CAPABILITY)
          .emittedEvent(WorkflowEventType.TOOL_INVOCATION_COMPLETED)
          .eventsInOrder(WorkflowEventType.TOOL_INVOCATION_REQUESTED,
              WorkflowEventType.TOOL_INVOCATION_COMPLETED)
          // The loopback body came back through the provider and was applied to run context.
          .contextEquals("tool." + CAPABILITY, RESPONSE_BODY);
    }
  }
}
