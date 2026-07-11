// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.executionestimator;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.estimate.RiskFlag;
import com.agentforge4j.core.workflow.estimate.WorkflowComplexityAnalysis;
import com.agentforge4j.core.workflow.estimate.WorkflowExecutionAnalysisService;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.LlmClientResolver;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.fake.FakeLlmClient;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Estimates the execution shape of the "baby AI Agent birth" target workflow using the shipped
 * {@code workflow-execution-estimator} catalog bundle: the bundle aggregates its own full
 * disclosure envelope in-workflow, before the run pauses for approval, so this example reads it
 * directly from context and shows it before deciding whether to approve — never approving first
 * and disclosing after.
 *
 * <p>The target workflow (what is being estimated) never runs; only the estimator workflow itself
 * runs. The target is loaded as a plain {@link WorkflowDefinition} for
 * {@link WorkflowExecutionAnalysisService#analyze(WorkflowDefinition)} to inspect structurally.
 */
public final class WorkflowExecutionEstimatorExample {

  static final String ESTIMATOR_WORKFLOW_ID = "workflow-execution-estimator";
  static final String ESTIMATE_STEP_ID = "estimate";
  static final String ESTIMATOR_AGENT_ID = "execution-estimator";
  static final String CALLER_ACTOR_ID = "adoption-center-example";

  private WorkflowExecutionEstimatorExample() {
  }

  /**
   * Loads the "baby AI Agent birth" target workflow definition. It is parsed as data only — never
   * registered with a running runtime, since it is analysed, not executed.
   *
   * @return the target workflow definition
   */
  static WorkflowDefinition loadTargetWorkflow() {
    String path = "/target-workflow/baby-agent-birth.workflow.json";
    try (InputStream stream = WorkflowExecutionEstimatorExample.class.getResourceAsStream(path)) {
      return new ObjectMapper().readValue(
          Objects.requireNonNull(stream, "Missing classpath resource: %s".formatted(path)),
          WorkflowDefinition.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load target workflow " + path, e);
    }
  }

  /**
   * Assembles an {@link AgentForge4j} instance with the shipped catalog enabled (bringing the
   * {@code workflow-execution-estimator} bundle and the {@code execution-estimator} agent onto the
   * classpath) and the {@code execution-estimator} agent's sizing response scripted deterministically.
   *
   * <p>The shipped agent declares real provider names ({@code openai}/{@code claude}/{@code ollama})
   * — production preferences, correctly left untouched for this example. Driving it with the fake
   * provider therefore needs a resolver that answers for every provider id and a selection strategy
   * that picks an agent's declared preference without checking availability; both are supplied here
   * rather than depending on the testkit's own (module-private) equivalents.
   *
   * @return the assembled runtime facade
   */
  static AgentForge4j assemble() {
    String sizingCommands = "[{\"type\":\"SET_CONTEXT\",\"key\":\"estimatedInputTokensPerAgentTurn\","
        + "\"value\":{\"type\":\"NUMBER\",\"value\":700}},"
        + "{\"type\":\"SET_CONTEXT\",\"key\":\"estimatedOutputTokensPerAgentTurn\","
        + "\"value\":{\"type\":\"NUMBER\",\"value\":350}},"
        + "{\"type\":\"SET_CONTEXT\",\"key\":\"estimatedToolInvocationsPerAgentTurn\","
        + "\"value\":{\"type\":\"NUMBER\",\"value\":0}},"
        + "{\"type\":\"COMPLETE\"}]";
    FakeScript script = new FakeScript(1, Map.of(
        new FakeScriptKey(ESTIMATOR_WORKFLOW_ID, ESTIMATE_STEP_ID, ESTIMATOR_AGENT_ID, 0),
        new FakeResponse(sizingCommands, null)));
    LlmClient fakeLlmClient = new FakeLlmClient(new StaticFakeResponseSource(script));

    // execution-estimator is workflow-specific and ships inside the workflow-execution-estimator
    // bundle's agents/ subfolder, not top-level shipped-agents/, so loading shipped workflows alone
    // is sufficient — withLoadShippedAgents(true) is not needed for this example.
    return AgentForge4jBootstrap.defaults()
        .withLoadShippedWorkflows(true)
        .withLlmClientResolver(new WildcardFakeLlmClientResolver(fakeLlmClient))
        .withLlmProviderSelectionStrategy(new FirstDeclaredPreferenceSelectionStrategy())
        .build();
  }

  /**
   * Resolves the single deterministic fake client for every provider id, so the example can drive
   * the shipped agent regardless of its declared (real) provider preferences. Sound because fake
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

  /** Context key prefix the {@code aggregate-estimate} step writes its disclosure fields under. */
  private static final String ESTIMATE_PREFIX = "executionEstimate.";

  /** Submitted value for {@code riskFlags} when the structural analysis raised none. */
  private static final String NO_RISK_FLAGS = "NONE";

  /**
   * Runs the estimator bundle against the target workflow's structural analysis and returns the
   * run's final state — the bundle aggregates its own full disclosure envelope in-workflow, before
   * the run pauses for approval, so this reads and shows that disclosure BEFORE deciding the
   * approval gate, never approving first and disclosing after.
   *
   * @param agentForge4j the assembled runtime facade
   * @param target       the target workflow to estimate
   *
   * @return the run's state after the approval decision, with the disclosure fields still present
   */
  static WorkflowState estimate(AgentForge4j agentForge4j, WorkflowDefinition target) {
    WorkflowComplexityAnalysis analysis = WorkflowExecutionAnalysisService.analyze(target);

    // Every fact the run needs travels once, as its own typed field, sourced directly from the same
    // analysis object — no independently-supplied JSON summary to duplicate or contradict it.
    String runId = agentForge4j.start(ESTIMATOR_WORKFLOW_ID);
    agentForge4j.runtime().submitInput(runId, Map.ofEntries(
        Map.entry("mode", "WORKFLOW_RUN"),
        Map.entry("complexity", analysis.complexityClass().name()),
        Map.entry("ceilingDerivable", String.valueOf(analysis.ceilingDerivable())),
        Map.entry("minimumRequiredTokens", String.valueOf(analysis.minimumRequiredTokens())),
        Map.entry("stepCount", String.valueOf(analysis.stepCount())),
        Map.entry("agentStepCount", String.valueOf(analysis.agentStepCount())),
        Map.entry("branchCount", String.valueOf(analysis.branchCount())),
        Map.entry("loopCount", String.valueOf(analysis.loopCount())),
        Map.entry("agentDrivenLoopCount", String.valueOf(analysis.agentDrivenLoopCount())),
        Map.entry("humanGateCount", String.valueOf(analysis.humanGateCount())),
        Map.entry("maxNestingDepth", String.valueOf(analysis.maxNestingDepth())),
        Map.entry("minAgentTurns", String.valueOf(analysis.minAgentTurns())),
        Map.entry("expectedAgentTurns", String.valueOf(analysis.expectedAgentTurns())),
        Map.entry("maxAgentTurns", String.valueOf(analysis.maxAgentTurns())),
        Map.entry("iterationCeiling", String.valueOf(analysis.iterationCeiling())),
        Map.entry("riskFlags", formatRiskFlags(analysis.riskFlags()))),
        CALLER_ACTOR_ID);

    WorkflowState paused = agentForge4j.runtime().getState(runId);
    if (paused.getStatus() != WorkflowStatus.AWAITING_STEP_APPROVAL) {
      throw new IllegalStateException(
          "Expected the estimator run to pause for approval but status was "
              + paused.getStatus());
    }

    // The bundle's aggregate-estimate step has already combined the structural analysis with the
    // agent's sizing into the full disclosure envelope before this pause — read and show it here,
    // BEFORE deciding the approval gate.
    printDisclosure(paused);

    agentForge4j.runtime().decideStepApproval(runId, paused.getCurrentStepId(),
        new StepApprovalDecision.Approve(CALLER_ACTOR_ID,
            "reviewed the disclosed estimate before continuing"));

    return agentForge4j.runtime().getState(runId);
  }

  private static String formatRiskFlags(List<RiskFlag> riskFlags) {
    return riskFlags.isEmpty()
        ? NO_RISK_FLAGS
        : riskFlags.stream().map(RiskFlag::name).collect(Collectors.joining(","));
  }

  private static void printDisclosure(WorkflowState state) {
    System.out.println("--- Execution estimate (disclosed before approval) ---");
    System.out.println("complexity: " + estimateContext(state, "complexity"));
    System.out.println("confidence: " + estimateContext(state, "confidence"));
    System.out.println("estimatedMinTokens: " + estimateContext(state, "estimatedMinTokens"));
    System.out.println("estimatedExpectedTokens: " + estimateContext(state, "estimatedExpectedTokens"));
    System.out.println("estimatedMaxTokens: " + estimateContext(state, "estimatedMaxTokens"));
    System.out.println("minimumRequiredTokens: " + estimateContext(state, "minimumRequiredTokens"));
    System.out.println("riskFlags: " + estimateContext(state, "riskFlags"));
    System.out.println("iterationCeiling: " + estimateContext(state, "iterationCeiling"));
    System.out.println("recommendation: " + estimateContext(state, "recommendation"));
  }

  private static Object estimateContext(WorkflowState state, String field) {
    return state.getContextValue(ESTIMATE_PREFIX + field)
        .orElseThrow(() -> new IllegalStateException(
            "Expected the estimator run to expose context key '%s%s' at the pause"
                .formatted(ESTIMATE_PREFIX, field)));
  }

  /**
   * Runs the example end to end.
   *
   * @param args unused
   */
  public static void main(String[] args) {
    WorkflowDefinition target = loadTargetWorkflow();
    AgentForge4j agentForge4j = assemble();
    estimate(agentForge4j, target);
  }
}
