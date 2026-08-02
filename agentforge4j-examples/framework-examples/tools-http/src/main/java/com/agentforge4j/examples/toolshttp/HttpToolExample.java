// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.toolshttp;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import com.agentforge4j.tools.http.BodyMode;
import com.agentforge4j.tools.http.HttpEndpointDefinition;
import com.agentforge4j.tools.http.HttpMethod;
import com.agentforge4j.tools.http.HttpToolProvider;
import com.agentforge4j.util.net.HttpEgressGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An embedded workflow whose agent calls an HTTP tool. The agent emits a single {@code TOOL_INVOCATION} (which runs
 * inline) followed by {@code COMPLETE}; the tool's response is recorded in the run context under
 * {@code tool.<capability>}.
 *
 * <p>The run is deterministic and offline. This module serves its own loopback HTTP endpoint
 * (127.0.0.1, an ephemeral port) and scripts the agent's one LLM call with the deterministic fake provider — no real
 * network, model, or API key is involved.
 */
public final class HttpToolExample {

  /**
   * Workflow id; matches {@code workflows/tools-http-demo.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "tools-http-demo";

  /**
   * The single step's id; matches the {@code stepId} in the workflow definition.
   */
  static final String STEP_ID = "lookup";

  /**
   * Agent id; matches {@code agents/tools-http-agent.agent/} and the step's {@code agentRef}.
   */
  static final String AGENT_ID = "tools-http-agent";

  /**
   * The HTTP tool's capability; the agent invokes it by this name.
   */
  static final String CAPABILITY = "weather.lookup";

  /**
   * Context key the tool result is stored under after the call.
   */
  static final String TOOL_CONTEXT_KEY = "tool." + CAPABILITY;

  /**
   * The scripted model output for the agent's single call: a bare JSON array that invokes the tool and then completes.
   * The tool runs inline between the two commands; the agent is not re-invoked.
   */
  static final String SCRIPTED_TOOL_THEN_COMPLETE =
      "[{\"type\":\"TOOL_INVOCATION\",\"capability\":\"weather.lookup\","
          + "\"arguments\":{\"city\":\"London\"}},{\"type\":\"COMPLETE\"}]";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpToolExample() {
  }

  /**
   * Serves the loopback endpoint, runs the workflow, prints the terminal status and tool result.
   *
   * @param args ignored
   *
   * @throws Exception if the stub server or a bundled resource cannot be set up
   */
  public static void main(String[] args) throws Exception {
    HttpServer stubServer = startStubServer();
    try {
      AgentForge4j agentForge4j = assemble(stubServer.getAddress().getPort());

      String runId = agentForge4j.start(WORKFLOW_ID);
      WorkflowState state = agentForge4j.runtime().getState(runId);

      System.out.printf("Workflow '%s' (run %s) finished with status: %s%n", WORKFLOW_ID, runId, state.getStatus());
      // On the documented deterministic path the result is always a StringContextValue; if the run
      // failed (or the key is absent), print what is there instead of crashing on the unwrap.
      ContextValue toolResult = state.getContext().get(TOOL_CONTEXT_KEY);
      String toolResultText = toolResult instanceof StringContextValue stringResult
          ? stringResult.value()
          : String.valueOf(toolResult);
      System.out.printf("Tool result (%s): %s%n", TOOL_CONTEXT_KEY, toolResultText);
    } finally {
      stubServer.stop(0);
    }
  }

  /**
   * Assembles an AgentForge4j runtime wired to the deterministic fake provider, this module's own config, and an HTTP
   * tool pointed at the loopback endpoint on {@code stubPort}.
   *
   * @param stubPort the port the loopback HTTP server is listening on
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble(int stubPort) throws URISyntaxException {
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(WORKFLOW_ID, STEP_ID, AGENT_ID, 0),
        new FakeResponse(SCRIPTED_TOOL_THEN_COMPLETE, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withAgentsDir(resourceDirectory("/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .withLlmClientResolver(new DefaultLlmClientResolver(List.of(fakeLlmClient)))
        .withToolProviders(List.of(httpToolProvider(stubPort)))
        // The secure default policy denies REMOTE_HTTP tools; this self-contained demo trusts its
        // own loopback tool, so it opts in with allowAll(). Production code must use a policy that
        // reflects its actual trust boundary, not a blanket allow.
        .withToolPolicy(ToolPolicy.allowAll())
        .build();
  }

  /**
   * Starts a minimal loopback HTTP server (127.0.0.1, ephemeral port) that answers the weather lookup with a fixed JSON
   * body. Ships with the example so the run needs no real network.
   *
   * @return the started server; the caller must {@code stop(...)} it
   *
   * @throws IOException if the server cannot bind
   */
  static HttpServer startStubServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/weather", exchange -> {
      byte[] body = "{\"city\":\"London\",\"summary\":\"Sunny\",\"tempC\":18}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream responseBody = exchange.getResponseBody()) {
        responseBody.write(body);
      }
    });
    server.start();
    return server;
  }

  private static ToolProvider httpToolProvider(int stubPort) {
    HttpEndpointDefinition endpoint = new HttpEndpointDefinition(
        CAPABILITY, "Weather lookup", "Reads the current weather for a city", false, HttpMethod.GET,
        "http://127.0.0.1:%d/weather/{city}".formatted(stubPort), citySchema(), null,
        Set.of(), BodyMode.NONE, Map.of(), Map.of(), null, null, false, null);
    // The endpoint is this example's own in-process loopback server (127.0.0.1). The fail-closed
    // egress guard blocks loopback and private addresses by default, so this deterministic local
    // demo opts into private-network egress with new HttpEgressGuard(true). This is a local-demo
    // exception only — production wiring must keep the default fail-closed guard.
    // Never follow redirects: a 30x to a private/metadata host would bypass the egress guard, which
    // validates only the originally mapped URL. Templates should always set Redirect.NEVER.
    HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    return new HttpToolProvider("demo", List.of(endpoint), reference -> null,
        httpClient, new HttpEgressGuard(true),
        new ToolExecutionOptions(Duration.ofSeconds(5), 0, Duration.ZERO), 65_536L, MAPPER);
  }

  private static JsonNode citySchema() {
    try {
      return MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid input schema", e);
    }
  }

  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        HttpToolExample.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
