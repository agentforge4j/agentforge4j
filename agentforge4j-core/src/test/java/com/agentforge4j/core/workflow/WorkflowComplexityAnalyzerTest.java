// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.estimate.ComplexityClass;
import com.agentforge4j.core.workflow.estimate.RiskFlag;
import com.agentforge4j.core.workflow.estimate.WorkflowComplexityAnalysis;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.core.workflow.step.loop.MaxIterationsAction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowComplexityAnalyzerTest {

  private static WorkflowDefinition workflow(String id, List<Executable> steps,
      Map<String, BlueprintDefinition> blueprints) {
    return new WorkflowDefinition(id, "W", null, null, null, "1.0.0", null, null, null,
        Map.of(), blueprints, steps);
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
