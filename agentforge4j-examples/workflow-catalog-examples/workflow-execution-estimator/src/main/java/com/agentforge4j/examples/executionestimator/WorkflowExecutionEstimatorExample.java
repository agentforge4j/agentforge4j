// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.executionestimator;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.core.runtime.StepApprovalDecision;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.estimate.ExecutionEstimate;
import com.agentforge4j.core.workflow.estimate.SizingInputs;
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

/**
 * Estimates the execution shape of the "baby AI Agent birth" target workflow using the shipped
 * {@code workflow-execution-estimator} catalog bundle, then demonstrates the compliant-caller
 * pattern the bundle's own contract requires: reading the paused run's sized figures, aggregating
 * them into the full disclosure envelope, and showing that disclosure before deciding whether to
 * approve — never approving first and disclosing after.
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

    return AgentForge4jBootstrap.defaults()
        .withLoadShippedWorkflows(true)
        .withLoadShippedAgents(true)
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

  /**
   * Runs the estimator bundle against the target workflow's structural analysis and returns the
   * final, aggregated estimate — after printing the mandatory disclosure and deciding the approval
   * gate, never before.
   *
   * @param agentForge4j the assembled runtime facade
   * @param target       the target workflow to estimate
   *
   * @return the aggregated execution estimate
   */
  static ExecutionEstimate estimate(AgentForge4j agentForge4j, WorkflowDefinition target) {
    WorkflowComplexityAnalysis analysis = WorkflowExecutionAnalysisService.analyze(target);
    String structuralSummaryJson =
        WorkflowExecutionAnalysisService.summarize(analysis, new ObjectMapper());

    String runId = agentForge4j.start(ESTIMATOR_WORKFLOW_ID);
    agentForge4j.runtime().submitInput(runId, Map.of(
        "mode", "WORKFLOW_RUN",
        "structuralSummaryJson", structuralSummaryJson,
        "complexity", analysis.complexityClass().name(),
        "ceilingDerivable", String.valueOf(analysis.ceilingDerivable()),
        "minimumRequiredTokens", String.valueOf(analysis.minimumRequiredTokens())),
        CALLER_ACTOR_ID);

    WorkflowState paused = agentForge4j.runtime().getState(runId);
    if (paused.getStatus() != WorkflowStatus.AWAITING_STEP_APPROVAL) {
      throw new IllegalStateException(
          "Expected the estimator run to pause for approval but status was "
              + paused.getStatus());
    }

    // The compliant-caller step: read the bundle's raw sized figures at the pause, aggregate them
    // deterministically, and disclose the full envelope BEFORE deciding the approval gate.
    SizingInputs sizing = readSizing(paused);
    ExecutionEstimate estimate = WorkflowExecutionAnalysisService.aggregate(analysis, sizing);
    printDisclosure(estimate);

    agentForge4j.runtime().decideStepApproval(runId, paused.getCurrentStepId(),
        new StepApprovalDecision.Approve(CALLER_ACTOR_ID,
            "reviewed the disclosed estimate before continuing"));

    return estimate;
  }

  private static SizingInputs readSizing(WorkflowState state) {
    return new SizingInputs(
        readNumber(state, "estimatedInputTokensPerAgentTurn"),
        readNumber(state, "estimatedOutputTokensPerAgentTurn"),
        readNumber(state, "estimatedToolInvocationsPerAgentTurn"));
  }

  private static int readNumber(WorkflowState state, String key) {
    ContextValue value = state.getContextValue(key)
        .orElseThrow(() -> new IllegalStateException(
            "Expected the estimator run to expose context key '%s' at the pause".formatted(key)));
    if (!(value instanceof NumberContextValue number)) {
      throw new IllegalStateException(
          "Expected context key '%s' to be numeric but was %s".formatted(key,
              value.getClass().getSimpleName()));
    }
    return number.value().intValue();
  }

  private static void printDisclosure(ExecutionEstimate estimate) {
    System.out.println("--- Execution estimate (disclosed before approval) ---");
    System.out.println("complexity: " + estimate.complexity());
    System.out.println("confidence: " + estimate.confidence());
    System.out.println("estimatedMinTokens: " + estimate.estimatedMinTokens());
    System.out.println("estimatedExpectedTokens: " + estimate.estimatedExpectedTokens());
    System.out.println("estimatedMaxTokens: " + estimate.estimatedMaxTokens());
    System.out.println("minimumRequiredTokens: " + estimate.minimumRequiredTokens());
    System.out.println("estimatedAgentTurns: " + estimate.estimatedAgentTurns());
    System.out.println("estimatedToolInvocations: " + estimate.estimatedToolInvocations());
    System.out.println("estimatedSteps: " + estimate.estimatedSteps());
    System.out.println("riskFlags: " + estimate.riskFlags());
    System.out.println("recommendation: " + estimate.recommendation());
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
