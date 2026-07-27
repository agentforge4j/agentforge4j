// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlspar;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmSecretReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A workflow-language example of {@code SPAR}: two agents reasoning against each other under workflow
 * control. A primary agent ({@code architect}) and a challenger ({@code developer}) exchange views over
 * bounded rounds; the exchange continues while a side asks for another round with a concrete reason (a
 * {@code CONTINUE} command with {@code wantsAnotherRound} true and a substantive reason). After the rounds,
 * the primary produces the final resolution and the step completes.
 *
 * <p>Here {@code maxRounds} is 2 and both sides ask for a second round in round one, so the exchange runs
 * both rounds and then resolves — a genuine multi-round SPAR. Each round's contributions are recorded in
 * the context under {@code spar.primary.round.N} and {@code spar.challenger.round.N}.
 *
 * <p>The example is LLM-agnostic. Out of the box it runs offline against the deterministic
 * {@code agentforge4j-llm-fake} provider (one scripted response per agent turn, no key, no network, no extra
 * dependency). Configure a provider key in {@code example.properties} (or via the environment), add a
 * provider module to this module's POM, <em>and</em> point both agents' {@code providerPreferences} at that
 * provider instead of {@code fake} to run the same workflow against a real model. The fake/real choice is
 * resolved by {@link ExampleLlmConfig}.
 */
public final class WlSparApp {

  /**
   * Workflow id; matches {@code workflows/wl-spar.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-spar";

  /**
   * The SPAR step's id; matches the {@code stepId} in the workflow.
   */
  static final String REVIEW_STEP_ID = "review";

  /**
   * The primary agent's id; matches {@code agents/architect.agent/} and the step's {@code agentRef}.
   * The primary also performs the resolution turn.
   */
  static final String PRIMARY_AGENT_ID = "architect";

  /**
   * The challenger agent's id; matches {@code agents/developer.agent/} and the SPAR
   * {@code challengerAgentId}.
   */
  static final String CHALLENGER_AGENT_ID = "developer";

  /**
   * The configured maximum number of exchange rounds. Must stay in sync with {@code maxRounds} in
   * {@code workflow.json}: the JSON is the runtime source of truth for the SPAR loop, and this
   * constant drives the test's per-round assertions.
   */
  static final int MAX_ROUNDS = 2;

  /**
   * Context key prefix under which each round's primary contribution is stored
   * ({@code spar.primary.round.<n>}).
   */
  static final String PRIMARY_ROUND_PREFIX = "spar.primary.round.";

  /**
   * Context key prefix under which each round's challenger contribution is stored
   * ({@code spar.challenger.round.<n>}).
   */
  static final String CHALLENGER_ROUND_PREFIX = "spar.challenger.round.";

  private WlSparApp() {
  }

  /**
   * Runs the SPAR workflow and prints the terminal status. The fake/real LLM choice is resolved from
   * configuration.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    ExampleLlmConfig config = ExampleLlmConfig.load();
    AgentForge4j agentForge4j = config.fakeLlm()
        ? assembleWithFake(WlSparFakeLlm.resolver())
        : assemble(config);

    String runId = agentForge4j.start(WORKFLOW_ID);
    WorkflowState state = agentForge4j.runtime().getState(runId);
    System.out.printf("status=%s%n", state.getStatus());
  }

  /**
   * Assembles an AgentForge4j runtime that resolves a real LLM provider from configuration — the
   * production-shaped path a copied example uses once a key and provider module are in place. The provider
   * factory is discovered from the classpath, so a matching provider module must be on this module's POM;
   * with none present the build fails fast with a clear "no provider factory" message.
   *
   * @param config the resolved example configuration carrying the provider name and API key
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assemble(ExampleLlmConfig config) throws URISyntaxException {
    return baseBuilder()
        .withLlmProvider(providerConfig(config))
        .build();
  }

  /**
   * Builds the real-provider configuration from {@code config}'s provider name, starting from that
   * provider's own {@link LlmProviderConfig} factory so the base URL and default model are populated (a
   * provider selected via configuration only must still resolve a working model).
   *
   * @param config the resolved example configuration carrying the provider name and API key
   *
   * @return a provider configuration with a working base URL/default model and the configured credential
   *
   * @throws IllegalArgumentException if {@code config.provider()} names a provider this template does not
   *         recognise
   */
  private static LlmProviderConfig providerConfig(ExampleLlmConfig config) {
    LlmProviderConfig.ProviderBuilder builder = switch (config.provider()) {
      case "openai" -> LlmProviderConfig.openai();
      case "claude" -> LlmProviderConfig.claude();
      case "ollama" -> LlmProviderConfig.ollama();
      case "vllm" -> LlmProviderConfig.vllm();
      case "gemini" -> LlmProviderConfig.gemini();
      case "mistral" -> LlmProviderConfig.mistral();
      case "azure-openai" -> LlmProviderConfig.azureOpenAi();
      case "openai-compatible" -> LlmProviderConfig.openAiCompatible();
      case "bedrock" -> LlmProviderConfig.bedrock();
      default -> throw new IllegalArgumentException(
          ("Unsupported value \"%s\" for agentforge4j.example.llm.provider: expected one of openai, claude, "
              + "ollama, vllm, gemini, mistral, azure-openai, openai-compatible, bedrock.")
              .formatted(config.provider()));
    };
    return builder.defaults()
        .apiKeyReference(LlmSecretReference.parse(config.apiKey()))
        .build();
  }

  /**
   * Assembles an AgentForge4j runtime wired to the given deterministic fake resolver, scripting each
   * agent's turn so the exchange runs both rounds and then resolves. Used by the offline run path and the
   * test, so both exercise exactly the same bootstrap wiring around a scripted fake — no key, no network.
   *
   * @param llmClientResolver the resolver to install (for example {@link WlSparFakeLlm#resolver()})
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  static AgentForge4j assembleWithFake(LlmClientResolver llmClientResolver) throws URISyntaxException {
    return baseBuilder()
        .withLlmClientResolver(llmClientResolver)
        .build();
  }

  private static AgentForge4jBootstrap.Builder baseBuilder() throws URISyntaxException {
    return AgentForge4jBootstrap.defaults()
        .withWorkflowsDir(resourceDirectory("/workflows"))
        .withAgentsDir(resourceDirectory("/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false);
  }

  /**
   * Resolves a directory on the classpath (e.g. {@code /workflows}) to a filesystem path. Works when
   * the example runs from exploded {@code target/classes} (IDE, tests, {@code mvn} build), which is
   * how examples are run from source.
   *
   * @param classpathDirectory absolute classpath path of the directory
   *
   * @return the resolved filesystem path
   *
   * @throws URISyntaxException if the resource URL is not a valid URI
   */
  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        WlSparApp.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
