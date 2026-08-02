// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlbranch;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.bootstrap.LlmProviderConfig;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.LlmSecretReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A workflow-language routing example. An agent step ({@code decide}) writes a {@code decision} into the
 * workflow context with a {@code SET_CONTEXT} command; a {@code BRANCH} step then routes on that context
 * value — {@code "approve"} runs an agent step to {@code COMPLETED}, {@code "reject"} ends the run via a
 * {@code FAIL} step ({@code FAILED}). Routing is deterministic and author-controlled: the {@code BRANCH}
 * step itself calls no model, it just reads a context value and dispatches.
 *
 * <p>The example is LLM-agnostic. Out of the box it runs offline against the deterministic
 * {@code agentforge4j-llm-fake} provider (no key, no network, no extra dependency). Configure a provider
 * key in {@code example.properties} (or via the environment), add a provider module to this module's POM,
 * <em>and</em> point both agents' {@code providerPreferences} at that provider instead of {@code fake} to
 * run the same workflow against a real model. The fake/real choice is resolved by
 * {@link ExampleLlmConfig}; the workflow structure is identical on both paths.
 */
public final class WlBranchApp {

  /**
   * Workflow id; matches {@code workflows/wl-branch.workflow/} and its {@code id} field.
   */
  static final String WORKFLOW_ID = "wl-branch";

  /**
   * The routing agent step's id; matches the {@code stepId} in the workflow.
   */
  static final String DECIDE_STEP_ID = "decide";

  /**
   * The routing agent's id; matches {@code agents/branch-agent.agent/} and the step's {@code agentRef}.
   */
  static final String BRANCH_AGENT_ID = "branch-agent";

  /**
   * The approved branch's inline step id; matches the {@code stepId} under the {@code "approve"} branch.
   */
  static final String APPROVE_STEP_ID = "approved";

  /**
   * The approved branch agent's id; matches {@code agents/approve-agent.agent/} and the branch's
   * {@code agentRef}.
   */
  static final String APPROVE_AGENT_ID = "approve-agent";

  /**
   * Context key the {@code decide} agent writes and the {@code BRANCH} step routes on.
   */
  static final String DECISION_KEY = "decision";

  /**
   * Branch value that routes to the agent-backed completion path.
   */
  static final String APPROVE = "approve";

  /**
   * Branch value that routes to the {@code FAIL} step.
   */
  static final String REJECT = "reject";

  private WlBranchApp() {
  }

  /**
   * Runs the example. On the offline fake path it drives the workflow once per branch and prints each
   * outcome (the fake scripts the decision deterministically). On the real-provider path the model
   * decides, so the workflow runs once and prints the resulting status.
   *
   * @param args ignored
   *
   * @throws URISyntaxException if a bundled resource directory cannot be resolved to a path
   */
  public static void main(String[] args) throws URISyntaxException {
    ExampleLlmConfig config = ExampleLlmConfig.load();
    if (config.fakeLlm()) {
      for (String decision : List.of(APPROVE, REJECT)) {
        AgentForge4j agentForge4j = assembleWithFake(WlBranchFakeLlm.resolver(decision));
        WorkflowState state = run(agentForge4j);
        System.out.printf("decision=%s -> status=%s%n", decision, state.getStatus());
      }
    } else {
      AgentForge4j agentForge4j = assemble(config);
      WorkflowState state = run(agentForge4j);
      System.out.printf("provider=%s -> status=%s%n", config.provider(), state.getStatus());
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
   * @param llmClientResolver the resolver to install (for example {@link WlBranchFakeLlm#resolver(String)})
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

  private static WorkflowState run(AgentForge4j agentForge4j) {
    String runId = agentForge4j.start(WORKFLOW_ID);
    return agentForge4j.runtime().getState(runId);
  }

  /**
   * Resolves a directory on the classpath (e.g. {@code /workflows}) to a filesystem path. Works when the
   * example runs from exploded {@code target/classes} (IDE, tests, {@code mvn} build), which is how
   * examples are run from source.
   *
   * @param classpathDirectory absolute classpath path of the directory
   *
   * @return the resolved filesystem path
   *
   * @throws URISyntaxException if the resource URL is not a valid URI
   */
  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(Objects.requireNonNull(
        WlBranchApp.class.getResource(classpathDirectory),
        "Missing classpath resource directory: %s".formatted(classpathDirectory)).toURI());
  }
}
