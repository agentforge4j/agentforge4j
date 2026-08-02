// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlhumanintheloop;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmSecretReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * A human-in-the-loop workflow expressed in the workflow language, with two suspend/resume gates:
 * an {@code INPUT} step that collects a request from a person, followed by an agent step gated by a
 * {@code HUMAN_APPROVAL} transition. The run suspends at {@code AWAITING_INPUT} until
 * {@link com.agentforge4j.core.runtime.WorkflowRuntime#submitInput} supplies the form, then the
 * agent does its work and the run suspends at {@code AWAITING_STEP_APPROVAL} until
 * {@link com.agentforge4j.core.runtime.WorkflowRuntime#decideStepApproval} decides. Approving
 * advances the run to {@code COMPLETED}; rejecting fails it with a {@code StepRejectionFailure}.
 *
 * <p>This is the workflow-language companion to {@code framework-examples/human-approval}: that
 * example introduces the suspend/resume basics; this one shows the language constructs (an
 * {@code INPUT} step with its artifact and the {@code HUMAN_APPROVAL} gate) and the resume-verb
 * contract together.
 *
 * <p>The example is LLM-agnostic. Out of the box it runs offline against the deterministic
 * {@code agentforge4j-llm-fake} provider (no key, no network, no extra dependency). Configure a provider
 * key in {@code example.properties} (or via the environment), add a provider module to this module's POM,
 * <em>and</em> point the reviewer agent's {@code providerPreferences} at that provider instead of
 * {@code fake} to run the same workflow against a real model. Either way the input and
 * the approval decision are supplied in code (they are human gates, not model output); only the reviewer
 * agent's step is served by the LLM. The fake/real choice is resolved by {@link ExampleLlmConfig}.
 */
public final class WlHumanInTheLoopApp {

  /**
   * Workflow id; matches {@code workflows/wl-human-in-the-loop.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-human-in-the-loop";

  /**
   * The input step's id; matches the {@code stepId} in the workflow.
   */
  static final String COLLECT_STEP_ID = "collect";

  /**
   * The gated review step's id; matches the {@code stepId} in the workflow and the approval call.
   */
  static final String REVIEW_STEP_ID = "review";

  /**
   * The reviewer agent's id; matches {@code agents/reviewer-agent.agent/} and the step's
   * {@code agentRef}.
   */
  static final String REVIEW_AGENT_ID = "reviewer-agent";

  /**
   * The input artifact's id; matches {@code request-form.artifact.json} and the input step's
   * {@code artifactId}.
   */
  static final String ARTIFACT_ID = "request-form";

  /**
   * The artifact item id collected from the user; answers are stored in the context under
   * {@code <artifactId>.<itemId>}.
   */
  static final String FORM_FIELD = "summary";

  private WlHumanInTheLoopApp() {
  }

  /**
   * Runs the workflow twice — once approved, once rejected — and prints each outcome, including the
   * two suspension points. The fake/real LLM choice is resolved once and applied to both paths.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    ExampleLlmConfig config = ExampleLlmConfig.load();
    runApprovePath(config);
    runRejectPath(config);
  }

  private static void runApprovePath(ExampleLlmConfig config) throws URISyntaxException {
    AgentForge4j agentForge4j = newRuntime(config);
    String runId = agentForge4j.start(WORKFLOW_ID);
    printStatus(agentForge4j, "approve — after start", runId);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "Ship the release"), "requester");
    printStatus(agentForge4j, "approve — after input", runId);

    agentForge4j.runtime().decideStepApproval(runId, REVIEW_STEP_ID,
        new StepApprovalDecision.Approve("alice", "Looks good"));
    printStatus(agentForge4j, "approve — after approve", runId);
  }

  private static void runRejectPath(ExampleLlmConfig config) throws URISyntaxException {
    AgentForge4j agentForge4j = newRuntime(config);
    String runId = agentForge4j.start(WORKFLOW_ID);

    agentForge4j.runtime().submitInput(runId, Map.of(FORM_FIELD, "Ship the release"), "requester");
    agentForge4j.runtime().decideStepApproval(runId, REVIEW_STEP_ID,
        new StepApprovalDecision.Reject("alice", "Needs rework"));

    WorkflowState rejected = agentForge4j.runtime().getState(runId);
    printStatus(agentForge4j, "reject — after reject", runId);
    System.out.printf("  failure reason: %s%n", rejected.getFailureReason());
  }

  /**
   * Builds the runtime for one run, choosing the fake or the real provider from {@code config}. The
   * fake path is fully offline; the real path discovers a provider factory from the classpath.
   *
   * @param config the resolved example configuration
   *
   * @return a ready-to-use framework facade
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  private static AgentForge4j newRuntime(ExampleLlmConfig config) throws URISyntaxException {
    return config.fakeLlm() ? assembleWithFake(WlHumanInTheLoopFakeLlm.resolver()) : assemble(config);
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
   * Assembles an AgentForge4j runtime wired to the given deterministic fake resolver. Used by the offline
   * run path and the test, so both exercise exactly the same bootstrap wiring around a scripted fake — no
   * key, no network.
   *
   * @param llmClientResolver the resolver to install (for example {@link WlHumanInTheLoopFakeLlm#resolver()})
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
        WlHumanInTheLoopApp.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
