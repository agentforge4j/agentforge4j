// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlloop;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmSecretReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A workflow-language example of bounded iteration via a looped blueprint, shown with two
 * termination strategies:
 *
 * <ul>
 *   <li>{@code wl-loop-fixed} — {@code FIXED_COUNT}: the loop body runs exactly {@code maxIterations}
 *       times regardless of what the agent emits (completion signals are ignored), then completes.</li>
 *   <li>{@code wl-loop-signal} — {@code AGENT_SIGNAL}: the loop runs until the agent emits a
 *       {@code COMPLETE} signal. Here the agent signals on its first iteration, so the loop stops at
 *       one iteration — well before its {@code maxIterations} ceiling — showing that the agent, not a
 *       fixed count, decides when to stop.</li>
 * </ul>
 *
 * <p>Both loops are declared as a {@code BLUEPRINT_REF} to a blueprint whose {@code behaviour}
 * carries the {@code loopConfig}. The example is LLM-agnostic: out of the box it runs offline against
 * the deterministic {@code agentforge4j-llm-fake} provider (one scripted response per iteration, no key,
 * no network, no extra dependency). Configure a provider key in {@code example.properties} (or via the
 * environment) <em>and</em> add a provider module to this module's POM to run the same workflows against a
 * real model — no code change. The fake/real choice is resolved by {@link ExampleLlmConfig}.
 */
public final class WlLoopApp {

  /**
   * Workflow id of the fixed-count loop; matches {@code workflows/wl-loop-fixed.workflow/}.
   */
  static final String FIXED_WORKFLOW_ID = "wl-loop-fixed";

  /**
   * Workflow id of the agent-signal loop; matches {@code workflows/wl-loop-signal.workflow/}.
   */
  static final String SIGNAL_WORKFLOW_ID = "wl-loop-signal";

  /**
   * The looped body step's id; matches the {@code stepId} in each blueprint.
   */
  static final String BODY_STEP_ID = "body";

  /**
   * The loop body agent's id; matches {@code agents/loop-agent.agent/} and each body's
   * {@code agentRef}.
   */
  static final String LOOP_AGENT_ID = "loop-agent";

  /**
   * The fixed-count loop's iteration count (its blueprint's {@code maxIterations}).
   */
  static final int FIXED_ITERATIONS = 3;

  /**
   * The number of iterations the agent-signal loop runs before the agent emits {@code COMPLETE} —
   * fewer than the blueprint's {@code maxIterations}, so termination is by the agent's signal, not
   * the ceiling.
   */
  static final int SIGNAL_ITERATIONS = 1;

  private WlLoopApp() {
  }

  /**
   * Runs both loop variants and prints each one's terminal status and iteration count. The fake/real
   * LLM choice is resolved from configuration and applied to both runs.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    ExampleLlmConfig config = ExampleLlmConfig.load();
    AgentForge4j agentForge4j = config.fakeLlm()
        ? assembleWithFake(WlLoopFakeLlm.resolver())
        : assemble(config);

    for (String workflowId : List.of(FIXED_WORKFLOW_ID, SIGNAL_WORKFLOW_ID)) {
      String runId = agentForge4j.start(workflowId);
      WorkflowState state = agentForge4j.runtime().getState(runId);
      System.out.printf("%s -> status=%s, iterations=%d%n",
          workflowId, state.getStatus(), loopIterations(agentForge4j, runId));
    }
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
   * provider selected via configuration only, with no code change, must still resolve a working model).
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
   * Assembles an AgentForge4j runtime wired to the given deterministic fake resolver, scripting one
   * response per iteration for each loop. Used by the offline run path and the test, so both exercise
   * exactly the same bootstrap wiring around a scripted fake — no key, no network.
   *
   * @param llmClientResolver the resolver to install (for example {@link WlLoopFakeLlm#resolver()})
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
   * Counts the loop iterations a run performed, by tallying its {@code LOOP_ITERATION_STARTED}
   * events. The event log is reached through {@code components().workflowEventLog()}, the internal
   * integrator accessor — it is not part of the public API, and an embedding application would
   * normally surface run progress through its own projection rather than read it here.
   *
   * @param agentForge4j the framework facade the run executed on
   * @param runId        the run to inspect
   *
   * @return the number of loop iterations started
   */
  static long loopIterations(AgentForge4j agentForge4j, String runId) {
    long iterations = 0;
    for (WorkflowEvent event : agentForge4j.components().workflowEventLog().getEvents(runId)) {
      if (event.eventType() == WorkflowEventType.LOOP_ITERATION_STARTED) {
        iterations++;
      }
    }
    return iterations;
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
        WlLoopApp.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
