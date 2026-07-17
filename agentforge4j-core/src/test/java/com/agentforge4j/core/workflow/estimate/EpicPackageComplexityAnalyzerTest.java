// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EpicPackageComplexityAnalyzerTest {

  private static EpicPackage packageOf(int epicCount) {
    List<Epic> epics = new ArrayList<>();
    for (int i = 0; i < epicCount; i++) {
      epics.add(new Epic("epic-" + i, "Epic " + i, null));
    }
    return new EpicPackage("pkg", epics);
  }

  @Test
  void rejectsNullPackage() {
    assertThatThrownBy(() -> EpicPackageComplexityAnalyzer.analyze(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void singleEpicIsSimple() {
    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(packageOf(1));

    assertThat(analysis.workflowId()).isEqualTo("pkg");
    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.SIMPLE);
    assertThat(analysis.loopCount()).isEqualTo(EpicPackageComplexityAnalyzer.PHASES_PER_EPIC);
    assertThat(analysis.agentDrivenLoopCount())
        .isEqualTo(EpicPackageComplexityAnalyzer.PHASES_PER_EPIC);
    assertThat(analysis.stepCount())
        .isEqualTo(EpicPackageComplexityAnalyzer.PHASES_PER_EPIC + 1);
    assertThat(analysis.minAgentTurns()).isEqualTo(5);
    assertThat(analysis.maxAgentTurns())
        .isEqualTo(EpicPackageComplexityAnalyzer.PHASES_PER_EPIC
            * EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE + 1);
    assertThat(analysis.ceilingDerivable()).isTrue();
    // Locks the positional constructor mapping (SV-4a): branch/human-gate/nesting are structurally
    // flat for an epic package (no branches, no human gates, no nesting), and the ceiling defaults to
    // the per-phase rework cap when no epic supplies a hint.
    assertThat(analysis.branchCount()).isZero();
    assertThat(analysis.humanGateCount()).isZero();
    assertThat(analysis.maxNestingDepth()).isEqualTo(1);
    assertThat(analysis.iterationCeiling())
        .isEqualTo(EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE);
  }

  @Test
  void noHintDefaultsExpectedIterationsToHalfTheReworkCeiling() {
    // packageOf() supplies no hint, so expectedIterations defaults to
    // Math.max(1, (DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE + 1) / 2) per epic — the branch none of
    // the other tests below exercise, since they all supply an explicit hint.
    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(packageOf(1));

    long expectedIterationsPerPhase = Math.max(1,
        (EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE + 1) / 2);
    assertThat(analysis.expectedAgentTurns())
        .isEqualTo(1 + EpicPackageComplexityAnalyzer.PHASES_PER_EPIC * expectedIterationsPerPhase);
  }

  @Test
  void twelveEpicsLoopCountModel() {
    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(packageOf(12));

    assertThat(analysis.loopCount()).isEqualTo(48);
    assertThat(analysis.agentDrivenLoopCount()).isEqualTo(48);
  }

  @Test
  void threeToFiveEpicsIsModerate() {
    assertThat(EpicPackageComplexityAnalyzer.analyze(packageOf(3)).complexityClass())
        .isEqualTo(ComplexityClass.MODERATE);
    assertThat(EpicPackageComplexityAnalyzer.analyze(packageOf(5)).complexityClass())
        .isEqualTo(ComplexityClass.MODERATE);
  }

  @Test
  void sixToTenEpicsIsComplex() {
    assertThat(EpicPackageComplexityAnalyzer.analyze(packageOf(6)).complexityClass())
        .isEqualTo(ComplexityClass.COMPLEX);
    assertThat(EpicPackageComplexityAnalyzer.analyze(packageOf(10)).complexityClass())
        .isEqualTo(ComplexityClass.COMPLEX);
  }

  @Test
  void elevenOrMoreEpicsIsHighRiskAndFlagged() {
    // 11 epics with no hint: epic count drives complexity/LARGE_STRUCTURE. HIGH_ITERATION_CEILING
    // never appears in Mode 2's risk flags at all under the capped-hint model — no epic count and no
    // hint can move iterationCeiling off the fixed default, so the flag is structurally unreachable
    // (see EpicPackageComplexityAnalyzer's class Javadoc) and is not evaluated, never merely absent
    // by coincidence.
    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(packageOf(11));

    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.HIGH_RISK);
    assertThat(analysis.riskFlags())
        .contains(RiskFlag.LARGE_STRUCTURE, RiskFlag.AGENT_DRIVEN_LOOP)
        .doesNotContain(RiskFlag.HIGH_ITERATION_CEILING);
    assertThat(analysis.iterationCeiling())
        .isEqualTo(EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE);
  }

  @Test
  void expectedReworkIterationsHintTightensExpectedTurns() {
    EpicPackage pkg = new EpicPackage("pkg", List.of(new Epic("e1", "E1", 2)));

    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(pkg);

    assertThat(analysis.expectedAgentTurns())
        .isEqualTo(1 + EpicPackageComplexityAnalyzer.PHASES_PER_EPIC * 2L);
    // A hint below the default ceiling doesn't touch the max envelope or iterationCeiling.
    assertThat(analysis.maxAgentTurns())
        .isEqualTo(1 + EpicPackageComplexityAnalyzer.PHASES_PER_EPIC
            * (long) EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE);
    assertThat(analysis.iterationCeiling())
        .isEqualTo(EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE);
  }

  @Test
  void hintAboveReworkCeilingIsCappedSoExpectedNeverExceedsMax() {
    EpicPackage pkg = new EpicPackage("pkg", List.of(new Epic("e1", "E1",
        EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE + 5)));

    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(pkg);

    assertThat(analysis.expectedAgentTurns()).isEqualTo(analysis.maxAgentTurns());
  }

  @Test
  void hintEqualToReworkCeilingYieldsExpectedEqualToMax() {
    EpicPackage pkg = new EpicPackage("pkg", List.of(new Epic("e1", "E1",
        EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE)));

    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(pkg);

    assertThat(analysis.expectedAgentTurns())
        .isEqualTo(1 + (long) EpicPackageComplexityAnalyzer.PHASES_PER_EPIC
            * EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE)
        .isEqualTo(analysis.maxAgentTurns());
  }

  @Test
  void cappedHintSurvivesAggregationIntoAValidEstimate() {
    EpicPackage pkg = new EpicPackage("pkg", List.of(new Epic("e1", "E1", 100)));
    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(pkg);

    ExecutionEstimate estimate = WorkflowExecutionAggregator.aggregate(
        analysis, new SizingInputs(400, 800, 0));

    assertThat(estimate.estimatedExpectedTokens())
        .isBetween(estimate.estimatedMinTokens(), estimate.estimatedMaxTokens());
  }

  @Test
  void rejectsEmptyEpicList() {
    assertThatThrownBy(() -> new EpicPackage("pkg", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDuplicateEpicIds() {
    assertThatThrownBy(() -> new EpicPackage("pkg",
        List.of(new Epic("e1", "E1", null), new Epic("e1", "E1 duplicate", null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("e1");
  }
}
