// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.estimate.ComplexityClass;
import com.agentforge4j.core.workflow.estimate.RiskFlag;
import com.agentforge4j.core.workflow.estimate.WorkflowComplexityAnalysis;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import com.agentforge4j.core.workflow.step.spar.SparConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowComplexityAnalyzerTest {

  private static WorkflowDefinition workflow(String id, List<Executable> steps,
      Map<String, BlueprintDefinition> blueprints) {
    return new WorkflowDefinition(id, "W", null, null, null, "1.0.0", null, null, null,
        Map.of(), blueprints, steps, List.of());
  }

  private static StepDefinition agentStep(String id, StepTransition transition) {
    return StepDefinition.builder()
        .withStepId(id)
        .withName(id)
        .withBehaviour(new AgentBehaviour("agent-" + id, transition, null))
        .build();
  }

  private static BlueprintDefinition loopBlueprint(String id, LoopConfig loop,
      List<Executable> body) {
    return new BlueprintDefinition(id, id, new BlueprintBehaviour(loop, StepTransition.AUTO), body);
  }

  @Test
  void rejectsNullRoot() {
    assertThatThrownBy(() -> WorkflowComplexityAnalyzer.analyze(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("root");
  }

  @Test
  void singleAgentStepIsSimpleWithDeterministicFloor() {
    WorkflowDefinition wf = workflow("wf", List.of(agentStep("s1", StepTransition.AUTO)), Map.of());

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.SIMPLE);
    assertThat(analysis.stepCount()).isEqualTo(1);
    assertThat(analysis.agentStepCount()).isEqualTo(1);
    assertThat(analysis.minAgentTurns()).isEqualTo(1);
    assertThat(analysis.expectedAgentTurns()).isEqualTo(1);
    assertThat(analysis.maxAgentTurns()).isEqualTo(1);
    assertThat(analysis.iterationCeiling()).isEqualTo(1);
    assertThat(analysis.ceilingDerivable()).isTrue();
    assertThat(analysis.riskFlags()).isEmpty();
    // 0 prompt chars / 4 + 1 agent step * 200 overhead + 100 base structure
    assertThat(analysis.minimumRequiredTokens()).isEqualTo(300);
  }

  @Test
  void fourAgentStepsAreModerate() {
    WorkflowDefinition wf = workflow("wf", List.of(
        agentStep("a", StepTransition.AUTO),
        agentStep("b", StepTransition.AUTO),
        agentStep("c", StepTransition.AUTO),
        agentStep("d", StepTransition.AUTO)), Map.of());

    assertThat(WorkflowComplexityAnalyzer.analyze(wf).complexityClass())
        .isEqualTo(ComplexityClass.MODERATE);
  }

  @Test
  void fixedCountLoopMultipliesTurnsAndIsComplex() {
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 3, MaxIterationsAction.FAIL);
    BlueprintDefinition bp = loopBlueprint("bp", loop, List.of(agentStep("in", StepTransition.AUTO)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp")), Map.of("bp", bp));

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.COMPLEX);
    assertThat(analysis.loopCount()).isEqualTo(1);
    assertThat(analysis.agentDrivenLoopCount()).isZero();
    assertThat(analysis.minAgentTurns()).isEqualTo(1);
    assertThat(analysis.expectedAgentTurns()).isEqualTo(3);
    assertThat(analysis.maxAgentTurns()).isEqualTo(3);
    assertThat(analysis.iterationCeiling()).isEqualTo(3);
  }

  @Test
  void expectedIterationsHintTightensExpectedTurns() {
    LoopConfig loop = new LoopConfig(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 10, MaxIterationsAction.FAIL, false, 2);
    BlueprintDefinition bp = loopBlueprint("bp", loop, List.of(agentStep("in", StepTransition.AUTO)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp")), Map.of("bp", bp));

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    assertThat(analysis.minAgentTurns()).isEqualTo(1);
    assertThat(analysis.expectedAgentTurns()).isEqualTo(2);
    assertThat(analysis.maxAgentTurns()).isEqualTo(10);
  }

  @Test
  void agentDrivenLoopIsHighRiskAndFlagged() {
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 3, MaxIterationsAction.AWAIT_USER);
    BlueprintDefinition bp = loopBlueprint("bp", loop, List.of(agentStep("in", StepTransition.AUTO)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp")), Map.of("bp", bp));

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.HIGH_RISK);
    assertThat(analysis.agentDrivenLoopCount()).isEqualTo(1);
    assertThat(analysis.riskFlags()).contains(RiskFlag.AGENT_DRIVEN_LOOP);
  }

  @Test
  void largeFixedCeilingIsHighRiskAndFlagged() {
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.FIXED_COUNT, null, null, 50, MaxIterationsAction.FAIL);
    BlueprintDefinition bp = loopBlueprint("bp", loop, List.of(agentStep("in", StepTransition.AUTO)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp")), Map.of("bp", bp));

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.HIGH_RISK);
    assertThat(analysis.iterationCeiling()).isEqualTo(50);
    assertThat(analysis.riskFlags()).contains(RiskFlag.HIGH_ITERATION_CEILING);
  }

  @Test
  void humanApprovalTransitionIsCountedAsGate() {
    WorkflowDefinition wf = workflow("wf",
        List.of(agentStep("s1", StepTransition.HUMAN_APPROVAL)), Map.of());

    assertThat(WorkflowComplexityAnalyzer.analyze(wf).humanGateCount()).isEqualTo(1);
  }

  @Test
  void branchStepIsCountedAndFlagged() {
    StepDefinition leaf = agentStep("leaf", StepTransition.AUTO);
    StepDefinition fail = StepDefinition.builder()
        .withStepId("def").withName("D").withBehaviour(new FailBehaviour("x")).build();
    BranchBehaviour branch = new BranchBehaviour("routeKey", Map.of("a", leaf), List.of(), fail, false);
    StepDefinition branchStep = StepDefinition.builder()
        .withStepId("b1").withName("Branch").withBehaviour(branch).build();
    WorkflowDefinition wf = workflow("wf", List.of(branchStep), Map.of());

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    assertThat(analysis.branchCount()).isEqualTo(1);
    assertThat(analysis.riskFlags()).contains(RiskFlag.LLM_DECIDED_BRANCHING);
  }

  @Test
  void sparStepWithMultipleRoundsAttributesPrimaryPlusChallengerPlusResolution() {
    SparConfig config = new SparConfig("challenger", 5, "resolve");
    StepDefinition spar = StepDefinition.builder()
        .withStepId("s1").withName("Spar")
        .withBehaviour(new SparBehaviour("agent", config, StepTransition.AUTO, null))
        .build();
    WorkflowDefinition wf = workflow("wf", List.of(spar), Map.of());

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    // min: one early-stopped round (2 invocations) + resolution = 3.
    assertThat(analysis.minAgentTurns()).isEqualTo(3);
    // max: 2 * maxRounds + 1 = 2 * 5 + 1 = 11.
    assertThat(analysis.maxAgentTurns()).isEqualTo(11);
    // expected: 2 * max(1, (5 + 1) / 2) + 1 = 2 * 3 + 1 = 7.
    assertThat(analysis.expectedAgentTurns()).isEqualTo(7);
  }

  @Test
  void evaluatorLoopAttributesOneEvaluatorTurnPerIteration() {
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.EVALUATOR, null, "evaluator-agent", 4, MaxIterationsAction.FAIL);
    BlueprintDefinition bp = loopBlueprint("bp", loop, List.of(agentStep("in", StepTransition.AUTO)));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp")), Map.of("bp", bp));

    WorkflowComplexityAnalysis analysis = WorkflowComplexityAnalyzer.analyze(wf);

    // body: 1 turn (min), 4 turns (max, using expectedIterations() default of max/2 rounded up = 2 for expected).
    // evaluator: +1 turn per iteration on top of the body.
    assertThat(analysis.minAgentTurns()).isEqualTo(1 + 1);
    assertThat(analysis.expectedAgentTurns()).isEqualTo(2 + 2);
    assertThat(analysis.maxAgentTurns()).isEqualTo(4 + 4);
  }

  @Test
  void branchOnlyNestingIsRecordedInMaxNestingDepth() {
    StepDefinition leaf = agentStep("leaf", StepTransition.AUTO);
    BranchBehaviour branch = new BranchBehaviour("routeKey", Map.of("a", leaf), List.of(), null, false);
    StepDefinition branchStep = StepDefinition.builder()
        .withStepId("b1").withName("Branch").withBehaviour(branch).build();
    WorkflowDefinition wf = workflow("wf", List.of(branchStep), Map.of());

    assertThat(WorkflowComplexityAnalyzer.analyze(wf).maxNestingDepth()).isEqualTo(1);
  }

  @Test
  void circularBlueprintReferenceFailsFast() {
    LoopConfig loop = LoopConfig.withDefaults(
        LoopTerminationStrategy.AGENT_SIGNAL, null, null, 1, MaxIterationsAction.FAIL);
    BlueprintDefinition selfRef = loopBlueprint("bp", loop, List.of(new BlueprintRef("bp")));
    WorkflowDefinition wf = workflow("wf", List.of(new BlueprintRef("bp")), Map.of("bp", selfRef));

    assertThatThrownBy(() -> WorkflowComplexityAnalyzer.analyze(wf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maximum nesting depth");
  }
}
