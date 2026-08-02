// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.agentcreator;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the shipped {@code agent-creator} catalog workflow end to end, playing the "AI Agent Adoption
 * Center" persona commissioning a new specialist agent: a short, warm welcome-message writer for
 * families who just adopted a baby AI Agent. Demonstrates the compliant-caller pattern the workflow's
 * own approval gate is designed around: reading the disclosed design preview, recommended tier, and
 * token estimate at the pause, showing that disclosure, and only then deciding whether to approve —
 * never approving first and disclosing after.
 *
 * <p>{@code agent-creator} shares five of its seven agents from the top-level {@code shipped-agents/}
 * catalog (only {@code agent-author} and {@code token-estimator} are workflow-local), so this example
 * loads both the shipped workflows and the shipped agents, unlike a workflow whose agents are entirely
 * bundle-local.
 */
public final class AgentCreatorExample {

  static final String WORKFLOW_ID = "agent-creator";
  static final String APPROVAL_STEP_ID = "estimate-tokens";
  static final String CALLER_ACTOR_ID = "adoption-center-example";
  private static final String AGENT_INTENT = "Create an agent that writes a short, warm welcome "
      + "message for a family that has just adopted a baby AI Agent from the AI Agent Adoption "
      + "Center, personalized with the agent's name and its quirks.";

  private AgentCreatorExample() {
  }

  /**
   * Assembles an {@link AgentForge4j} instance with the shipped catalog enabled (bringing the {@code
   * agent-creator} bundle, its two workflow-local agents, and its five shared {@code shipped-agents/}
   * agents onto the classpath) and every agent step's response scripted deterministically.
   *
   * <p>The shipped agents declare real provider names ({@code openai}/{@code claude}/{@code ollama})
   * — production preferences, correctly left untouched for this example. Driving them with the fake
   * provider therefore needs a resolver that answers for every provider id and a selection strategy
   * that picks an agent's declared preference without checking availability; both are supplied here
   * rather than depending on the testkit's own (module-private) equivalents.
   *
   * @return the assembled runtime facade
   */
  static AgentForge4j assemble() {
    FakeScript script = new FakeScriptParser().parse(readScriptResource());
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    return AgentForge4jBootstrap.defaults()
        .withLoadShippedWorkflows(true)
        .withLoadShippedAgents(true)
        .withLlmClientResolver(new WildcardFakeLlmClientResolver(fakeLlmClient))
        .withLlmProviderSelectionStrategy(new FirstDeclaredPreferenceSelectionStrategy())
        .build();
  }

  private static String readScriptResource() {
    String path = "/agent-creator-script.json";
    try (InputStream stream = AgentCreatorExample.class.getResourceAsStream(path)) {
      return new String(
          Objects.requireNonNull(stream, "Missing classpath resource: %s".formatted(path))
              .readAllBytes(),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + path, e);
    }
  }

  /**
   * Resolves the single deterministic fake client for every provider id, so the example can drive
   * the shipped agents regardless of their declared (real) provider preferences. Sound because fake
   * responses are keyed by workflow/step/agent/ordinal, never by provider.
   */
  private static final class WildcardFakeLlmClientResolver implements LlmClientResolver {

    private final LlmClient fake;

    private WildcardFakeLlmClientResolver(LlmClient fake) {
      this.fake = fake;
    }

    @Override
    public LlmClient resolve(String provider) {
      return fake;
    }

    @Override
    public boolean isProviderAvailable(String provider) {
      return true;
    }

    @Override
    public List<String> listAvailableClients() {
      return List.of();
    }
  }

  /**
   * Selects an agent's first declared provider preference without consulting the available-provider
   * enumeration — sound only paired with {@link WildcardFakeLlmClientResolver}, which resolves every
   * provider id to the same fake client.
   */
  private static final class FirstDeclaredPreferenceSelectionStrategy
      implements LlmProviderSelectionStrategy {

    @Override
    public ProviderPreference selectInitialProvider(AgentDefinition agent,
        List<String> availableProviders) {
      return agent.providerPreferences().stream()
          .findFirst()
          .orElseThrow(() -> new LlmInvocationException(
              "Agent '%s' declares no provider preferences".formatted(agent.id())));
    }
  }

  /**
   * Runs {@code agent-creator} to its approval pause, discloses the design preview, recommended tier,
   * and token estimate, approves, and returns the final workflow state after the run completes.
   *
   * @param agentForge4j the assembled runtime facade
   *
   * @return the final workflow state
   */
  static WorkflowState run(AgentForge4j agentForge4j) {
    String runId = agentForge4j.start(WORKFLOW_ID);
    agentForge4j.runtime().submitInput(runId, Map.of("agent-intent", AGENT_INTENT),
        CALLER_ACTOR_ID);

    WorkflowState paused = agentForge4j.runtime().getState(runId);
    if (paused.getStatus() != WorkflowStatus.AWAITING_STEP_APPROVAL) {
      throw new IllegalStateException(
          "Expected the agent-creator run to pause for approval but status was "
              + paused.getStatus());
    }

    // The compliant-caller step: disclose the design preview, recommended tier, and token estimate
    // BEFORE deciding the approval gate.
    printDisclosure(paused);

    agentForge4j.runtime().decideStepApproval(runId, APPROVAL_STEP_ID,
        new StepApprovalDecision.Approve(CALLER_ACTOR_ID,
            "reviewed the disclosed design preview and estimate before continuing"));

    return agentForge4j.runtime().getState(runId);
  }

  private static void printDisclosure(WorkflowState state) {
    System.out.println("--- Design preview and estimate (disclosed before approval) ---");
    System.out.println("agentId: " + readString(state, "agentId"));
    System.out.println("recommendedTier: " + readString(state, "recommendedTier"));
    System.out.println("ruleFired: " + readString(state, "ruleFired"));
    System.out.println("designSummary: " + readString(state, "designSummary"));
    System.out.println("tokenEstimate: " + readString(state, "tokenEstimate"));
  }

  private static String readString(WorkflowState state, String key) {
    ContextValue value = state.getContextValue(key)
        .orElseThrow(() -> new IllegalStateException(
            "Expected the agent-creator run to expose context key '%s' at the pause"
                .formatted(key)));
    if (!(value instanceof StringContextValue string)) {
      throw new IllegalStateException(
          "Expected context key '%s' to be a string but was %s".formatted(key,
              value.getClass().getSimpleName()));
    }
    return string.value();
  }

  private static void printOutcome(WorkflowState finalState) {
    System.out.println("--- Outcome ---");
    System.out.println("status: " + finalState.getStatus());
    System.out.println("verdict: " + readString(finalState, "verdict"));
  }

  /**
   * Runs the example end to end.
   *
   * @param args unused
   */
  public static void main(String[] args) {
    AgentForge4j agentForge4j = assemble();
    WorkflowState finalState = run(agentForge4j);
    printOutcome(finalState);
  }
}
