// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.agent.AgentBundleArtifactValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.testkit.assertion.WorkflowRunAssert;
import com.agentforge4j.testkit.capture.WorkflowRunResult;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.scenario.GateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Drives the rebuilt {@code agent-creator} workflow through the testkit harness over scripted fake-LLM responses,
 * covering the behaviour the shipped happy-path Case-A scenario cannot assert in one run: the deterministic
 * {@code [4]} tier-resolution matrix, the pre-approval no-files guarantee, and the fail-closed paths
 * ({@code [8v]} validation, {@code [10b]} verdict, unmatched-branch, and {@code supportedCommands} enforcement).
 */
class AgentCreatorWorkflowTest {

  private static final String WORKFLOW_ID = "agent-creator";
  private static final Map<String, String> INTENT =
      Map.of("agent-intent", "Create an agent that summarises PDF documents into a short brief.");
  private static final Map<String, String> CLARIFICATIONS =
      Map.of("clarificationAnswers", "The domain is document processing; output a short brief.");

  // --- Deterministic [4] tier resolution: assessment -> recommendedTier + ruleFired ---

  @ParameterizedTest(name = "{0} -> {1}/{2}")
  @CsvSource({
      "tier-lite,                  LITE,     COMPLEXITY_RISK_MATRIX",
      "tier-standard,              STANDARD, COMPLEXITY_RISK_MATRIX",
      "tier-powerful-matrix,       POWERFUL, COMPLEXITY_RISK_MATRIX",
      "tier-powerful-sensitivity,  POWERFUL, SENSITIVITY_FLOOR"
  })
  void resolvesRecommendedTierDeterministically(String script, String tier, String rule) {
    WorkflowRunResult result = run(script, List.of(GateResponse.input(INTENT)));

    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_STEP_APPROVAL)
        .contextEquals("recommendedTier", tier)
        .contextEquals("ruleFired", rule);
  }

  // --- Approval gate: no bundle files are written before approval ---

  @Test
  void writesNoFilesBeforeApproval() {
    WorkflowRunResult result = run("happy", List.of(GateResponse.input(INTENT)));

    WorkflowRunAssert.assertThat(result)
        .reachedPendingState(WorkflowStatus.AWAITING_STEP_APPROVAL)
        .didNotVisitStep("generate-agent")
        .artifactAbsent("agent.json")
        .artifactAbsent("systemprompt.md")
        .artifactAbsent("README.md");
  }

  // --- Fail-closed paths ---

  @Test
  void failsClosedOnUnknownComplexity() {
    WorkflowRunResult result = run("fail-unknown-complexity", List.of(GateResponse.input(INTENT)));

    WorkflowRunAssert.assertThat(result).isFailed();
  }

  @Test
  void failsClosedWhenGeneratedModelTierDoesNotMatchRecommendedTier() {
    WorkflowRunResult result = run("validate-fail-modeltier",
        List.of(GateResponse.input(INTENT), GateResponse.approveStep("ok")));

    WorkflowRunAssert.assertThat(result).isFailed();
    assertThat(result.finalState().getStatus()).isEqualTo(WorkflowStatus.FAILED);
  }

  @Test
  void failsClosedOnBlockingVerdict() {
    WorkflowRunResult result = run("verdict-blocking",
        List.of(GateResponse.input(INTENT), GateResponse.approveStep("ok")));

    // Assert the run reached the audited FAIL step (its reason), not merely that it failed.
    WorkflowRunAssert.assertThat(result).isFailed().failedBecause("BLOCKING_ISSUES");
  }

  @Test
  void failsClosedOnUnmatchedSensitivityFloor() {
    // The outer resolve-tier branch (sensitivityFloor) has no defaultBranch and failOnUnmatched=true;
    // a value that is neither NONE nor SENSITIVE fails closed (distinct from the inner-matrix case).
    WorkflowRunResult result =
        run("resolve-tier-unmatched-floor", List.of(GateResponse.input(INTENT)));

    WorkflowRunAssert.assertThat(result).isFailed();
  }

  @Test
  void validateFinalCatchesModelTierMismatchIntroducedAfterValidateAgent() {
    // generate-agent writes modelTier=LITE (passes [8v]); generate-verification overwrites agent.json
    // with modelTier=STANDARD (CREATE_FILE is last-write-wins); [9v]'s equality contract catches it.
    WorkflowRunResult result = run("validate-final-modeltier-mismatch",
        List.of(GateResponse.input(INTENT), GateResponse.approveStep("ok")));

    WorkflowRunAssert.assertThat(result).isFailed().failedBecause("modelTier");
  }

  @Test
  void clarifyTruePathCollectsAndFlowsDownstream() {
    // clarificationNeeded="true" routes to the collect-clarifications INPUT step; the submitted answer is
    // consumed by the downstream assessor (via inputKeys), so reaching completion through that branch
    // proves it flowed. (INPUT answers live in the untrusted-input envelope, not as a top-level context
    // value — unlike the skip path's ASSIGN_CONTEXT — so this asserts the branch + completion, not a raw key.)
    WorkflowRunResult result = run("clarify-true",
        List.of(GateResponse.input(INTENT), GateResponse.input(CLARIFICATIONS),
            GateResponse.approveStep("ok")));

    WorkflowRunAssert.assertThat(result)
        .isCompleted()
        .visitedStep("collect-clarifications");
  }

  @Test
  void failsClosedWhenNonGeneratorAgentAttemptsCreateFile() {
    WorkflowRunResult result =
        run("command-permission-violation", List.of(GateResponse.input(INTENT)));

    WorkflowRunAssert.assertThat(result).isFailed();
  }

  @Test
  void failsClosedOnMalformedVerificationJson() {
    // [9v] uses the agent-creator-bundle validator, which structurally validates the generated
    // verification starter; a verification/script.json that is not valid JSON fails the run closed.
    WorkflowRunResult result = run("verify-fail-malformed-verification",
        List.of(GateResponse.input(INTENT), GateResponse.approveStep("ok")));

    WorkflowRunAssert.assertThat(result).isFailed();
  }

  @Test
  void failsClosedWhenARequiredBundleFileIsMissing() {
    // The generator omits README.md; the [8v] required-artifact allowlist must reject the bundle.
    WorkflowRunResult result = run("validate-fail-missing-readme",
        List.of(GateResponse.input(INTENT), GateResponse.approveStep("ok")));

    WorkflowRunAssert.assertThat(result).isFailed();
  }

  // --- Shipped example bundle (must exist and stay coherent) ---

  @Test
  void exampleBundleLoadsAsValidAgentDefinitionWithLiteTier() throws IOException {
    String base = "/shipped-workflows/agent-creator.workflow/examples/pdf-summarizer/";
    String agentJson = readResource(base + "agent.json");
    String systemPrompt = readResource(base + "systemprompt.md");
    Map<String, String> bundle = Map.of("agent.json", agentJson, "systemprompt.md", systemPrompt);

    ValidationResult result = new AgentBundleArtifactValidator().validate(() -> bundle);
    assertThat(result.valid())
        .as("example pdf-summarizer bundle must load as a valid agent definition").isTrue();

    JsonNode parsed = new ObjectMapper().readTree(agentJson);
    assertThat(parsed.at("/modelTier").asText())
        .as("example modelTier must be LITE, matching its README and the tier rule").isEqualTo("LITE");
  }

  // --- In-bundle docs (drift guard) ---

  @Test
  void inBundleDocsCoverTheRequiredTopics() {
    String workflowReadme = readResource("/shipped-workflows/agent-creator.workflow/README.md");
    assertThat(workflowReadme)
        .as("workflow README must explain purpose, approval gate, tier resolution, validation, "
            + "verification starter, and the example location")
        .contains("approval", "tier", "valid", "verification", "example");

    String exampleReadme =
        readResource("/shipped-workflows/agent-creator.workflow/examples/pdf-summarizer/README.md");
    assertThat(exampleReadme)
        .as("example README must name its files and its LITE tier")
        .contains("LITE", "agent.json", "systemprompt.md");
  }

  private static WorkflowRunResult run(String scriptName, List<GateResponse> gates) {
    FakeScript script = new FakeScriptParser().parse(readScript(scriptName));
    WorkflowTestHarness harness =
        WorkflowTestHarness.builder().shippedCatalog(true).script(script).build();
    return harness.run(WORKFLOW_ID, gates);
  }

  private static String readScript(String name) {
    return readResource("/agent-creator/" + name + ".script.json");
  }

  private static String readResource(String path) {
    try (InputStream in = AgentCreatorWorkflowTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
