// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlretry;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmSecretReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * A workflow-language example of {@code RETRY_PREVIOUS}: re-executing an earlier step. The
 * {@code request} step collects a note via {@code INPUT}; a {@code RETRY_PREVIOUS} step then rewinds
 * to it ({@code FROM_STEP}, {@code maxAttempts} 1), so the input is requested a second time. Once the
 * single retry attempt is exhausted, the run falls through to the {@code fallback} agent step and
 * completes.
 *
 * <p>The re-executed step is the {@code INPUT} step on purpose: {@code RETRY_PREVIOUS} rewinds the
 * run and re-runs the previous step, and a re-run {@code INPUT} step re-suspends at
 * {@code AWAITING_INPUT} — which is the observable proof that the rewind happened. (A re-run plain
 * agent step would have nothing to suspend on.) The two {@code submitInput} calls in
 * {@link #main(String[])} and the test correspond to the original request and the retry's re-request.
 *
 * <p>The example is LLM-agnostic. Out of the box it runs offline against the deterministic
 * {@code agentforge4j-llm-fake} provider (no key, no network, no extra dependency). Configure a provider
 * key in {@code example.properties} (or via the environment) <em>and</em> add a provider module to this
 * module's POM to run the same workflow against a real model — no code change. The only model call is the
 * fallback agent's; the inputs are supplied in code on both paths. The fake/real choice is resolved by
 * {@link ExampleLlmConfig}.
 */
public final class WlRetryApp {

  /**
   * Workflow id; matches {@code workflows/wl-retry.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-retry";

  /**
   * The input step that {@code RETRY_PREVIOUS} rewinds to; matches the {@code stepId} and the
   * {@code retryStepId}.
   */
  static final String REQUEST_STEP_ID = "request";

  /**
   * The input artifact's id; matches {@code retry-form.artifact.json} and the input step's
   * {@code artifactId}.
   */
  static final String ARTIFACT_ID = "retry-form";

  /**
   * The artifact item id collected from the user.
   */
  static final String FORM_FIELD = "note";

  /**
   * The fallback agent's id; matches {@code agents/finalize-agent.agent/} and the fallback step's
   * {@code agentRef}. The fallback runs once the retry attempts are exhausted.
   */
  static final String FINALIZE_AGENT_ID = "finalize-agent";

  /**
   * The fallback step's id; matches the inline {@code fallback} step's {@code stepId}.
   */
  static final String FINALIZE_STEP_ID = "finalize";

  private WlRetryApp() {
  }

  /**
   * Runs the workflow, supplying the note twice (original request and retry re-request), and prints
   * the status after each step. The fake/real LLM choice is resolved from configuration.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    ExampleLlmConfig config = ExampleLlmConfig.load();
    AgentForge4j agentForge4j = config.fakeLlm()
        ? assembleWithFake(WlRetryFakeLlm.resolver())
        : assemble(config);

    String runId = agentForge4j.start(WORKFLOW_ID);
    printStatus(agentForge4j, "after start", runId);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "first note"), "requester");
    printStatus(agentForge4j, "after first input (retry rewinds, re-requests)", runId);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "second note"), "requester");
    printStatus(agentForge4j, "after second input (attempts exhausted, fallback)", runId);
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
   * Assembles an AgentForge4j runtime wired to the given deterministic fake resolver. Used by the offline
   * run path and the test, so both exercise exactly the same bootstrap wiring around a scripted fake — no
   * key, no network.
   *
   * @param llmClientResolver the resolver to install (for example {@link WlRetryFakeLlm#resolver()})
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

  private static void printStatus(AgentForge4j agentForge4j, String label, String runId) {
    WorkflowState state = agentForge4j.runtime().getState(runId);
    System.out.printf("%s: %s%n", label, state.getStatus());
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
        WlRetryApp.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
