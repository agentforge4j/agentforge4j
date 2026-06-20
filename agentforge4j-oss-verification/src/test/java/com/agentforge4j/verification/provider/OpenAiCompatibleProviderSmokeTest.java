// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.DefaultLlmClientResolver;
import com.agentforge4j.llm.LlmClientConfiguration;
import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmProviderOptions;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.openaicompatible.OpenAiCompatibleLlmClientFactory;
import com.agentforge4j.verification.support.Fixtures;
import com.agentforge4j.verification.support.LoopbackHttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tier — real-provider smoke (offline). Drives a one-step agent workflow through the genuine
 * {@code agentforge4j-llm-openai-compatible} client — serialize → HTTP → parse — against a loopback
 * stub returning a canned OpenAI Responses payload whose assistant text is the agent's
 * {@code COMPLETE} command. Proves the shipped provider path end-to-end with no network and no keys.
 *
 * <p>Two tiers are covered: {@link #openAiCompatibleProviderDrivesAWorkflowThroughTheRealHttpPath()}
 * wires the client directly through {@link AgentForge4jBootstrap.Builder#withLlmClientResolver} with
 * a {@link DefaultLlmClientResolver} (the HTTP serialize/parse proof), and
 * {@link #publicBootstrapProviderConfigDiscoversAndDrivesTheRealClient()} drives the same loopback
 * through the public {@link AgentForge4jBootstrap.Builder#withLlmProvider(LlmProviderConfig)} +
 * {@code ServiceLoader} discovery path, proving the neutral provider-config seam (#98/#103) carries
 * the openai-compatible base-url / responses-path / auth options and credential to the discovered
 * factory.
 */
class OpenAiCompatibleProviderSmokeTest {

  // OpenAI Responses payload; the assistant text is the agent's completion command JSON.
  private static final String COMPLETION_JSON =
      "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\","
          + "\"text\":\"[{\\\"type\\\":\\\"COMPLETE\\\"}]\"}]}],"
          + "\"model\":\"smoke-model\",\"usage\":{\"input_tokens\":4,\"output_tokens\":2}}";

  @Test
  void openAiCompatibleProviderDrivesAWorkflowThroughTheRealHttpPath() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(COMPLETION_JSON)) {
      LlmClient client = new OpenAiCompatibleLlmClientFactory().create(
          new LlmClientFactoryContext(new ObjectMapper(),
              new LoopbackConfig(server.baseUri().toString()),
              reference -> new LlmSecret(reference.literalValue())));

      AgentForge4j af = AgentForge4jBootstrap.defaults()
          .withLlmClientResolver(new DefaultLlmClientResolver(List.of(client)))
          .withLoadShippedWorkflows(false)
          .withLoadShippedAgents(false)
          .withWorkflowsDir(Fixtures.dir("/fixtures/provider/workflows"))
          .withAgentsDir(Fixtures.dir("/fixtures/provider/agents"))
          .build();

      WorkflowRuntime runtime = af.runtime();
      String runId = runtime.start("provider-smoke");
      WorkflowState state = runtime.getState(runId);

      assertThat(state.getStatus())
          .as("the run must complete through the real provider HTTP round-trip")
          .isEqualTo(WorkflowStatus.COMPLETED);
      assertThat(server.capturedRequestBody())
          .as("the real openai-compatible client must have serialized and POSTed the request")
          .contains("smoke-model");
    }
  }

  @Test
  void publicBootstrapProviderConfigDiscoversAndDrivesTheRealClient() throws Exception {
    try (LoopbackHttpServer server = new LoopbackHttpServer(COMPLETION_JSON)) {
      // Public path: supply the provider via withLlmProvider(...) carrying base-url, credential, and
      // the openai-compatible options through the neutral seam; the client is discovered by
      // ServiceLoader (no withLlmClientResolver / factory.create()).
      AgentForge4j af = AgentForge4jBootstrap.defaults()
          .withLlmProvider(LlmProviderConfig.openAiCompatible()
              .defaults()
              .baseUrl(server.baseUri().toString())
              .defaultModel("smoke-model")
              .apiKey("test-key")
              .option("auth.header.name", "Authorization")
              .option("auth.header.prefix", "Bearer ")
              .option("request.timeout", "PT30S")
              .option("responses.path", "/v1/responses")
              .build())
          .withLoadShippedWorkflows(false)
          .withLoadShippedAgents(false)
          .withWorkflowsDir(Fixtures.dir("/fixtures/provider/workflows"))
          .withAgentsDir(Fixtures.dir("/fixtures/provider/agents"))
          .build();

      WorkflowRuntime runtime = af.runtime();
      String runId = runtime.start("provider-smoke");
      WorkflowState state = runtime.getState(runId);

      assertThat(state.getStatus())
          .as("the discovered client must drive the run to completion over the loopback")
          .isEqualTo(WorkflowStatus.COMPLETED);
      assertThat(server.capturedRequestBody())
          .as("base-url + default-model carried through the public config must reach the endpoint")
          .contains("smoke-model");
      assertThat(server.capturedHeader("Authorization"))
          .as("apiKey + auth-header options carried through the neutral seam must be applied")
          .isEqualTo("Bearer test-key");
    }
  }

  /**
   * Minimal in-test neutral {@link LlmClientConfiguration} pointing the client at the loopback,
   * carrying the openai-compatible-specific settings through {@link LlmProviderOptions} (the #98/#103
   * neutral provider-config seam).
   */
  private static final class LoopbackConfig implements LlmClientConfiguration {

    private final String baseUrl;

    LoopbackConfig(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    @Override
    public String getProviderName() {
      return "openai-compatible";
    }

    @Override
    public String getDefaultModel() {
      return "smoke-model";
    }

    @Override
    public Duration getConnectTimeout() {
      return Duration.ofSeconds(10);
    }

    @Override
    public String getBaseUrl() {
      return baseUrl;
    }

    @Override
    public Optional<LlmSecretReference> getApiKeyReference() {
      return Optional.of(LlmSecretReference.literal("test-key"));
    }

    @Override
    public LlmProviderOptions getOptions() {
      Map<String, String> options = new HashMap<>();
      options.put("auth.header.name", "Authorization");
      options.put("auth.header.prefix", "Bearer ");
      options.put("request.timeout", "PT30S");
      options.put("responses.path", "/v1/responses");
      return LlmProviderOptions.of("openai-compatible", options);
    }
  }
}
