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
    assertThat(analysis.loopCount()).isEqualTo(1);
    assertThat(analysis.agentDrivenLoopCount()).isEqualTo(1);
    assertThat(analysis.stepCount())
        .isEqualTo(EpicPackageComplexityAnalyzer.PHASES_PER_EPIC + 1);
    assertThat(analysis.minAgentTurns()).isEqualTo(5);
    assertThat(analysis.maxAgentTurns())
        .isEqualTo(EpicPackageComplexityAnalyzer.PHASES_PER_EPIC
            * EpicPackageComplexityAnalyzer.DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE + 1);
    assertThat(analysis.ceilingDerivable()).isTrue();
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
    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(packageOf(11));

    assertThat(analysis.complexityClass()).isEqualTo(ComplexityClass.HIGH_RISK);
    assertThat(analysis.riskFlags())
        .contains(RiskFlag.LARGE_STRUCTURE, RiskFlag.HIGH_ITERATION_CEILING,
            RiskFlag.AGENT_DRIVEN_LOOP);
  }

  @Test
  void expectedReworkIterationsHintTightensExpectedTurns() {
    EpicPackage pkg = new EpicPackage("pkg", List.of(new Epic("e1", "E1", 2)));

    WorkflowComplexityAnalysis analysis = EpicPackageComplexityAnalyzer.analyze(pkg);

    assertThat(analysis.expectedAgentTurns())
        .isEqualTo(1 + EpicPackageComplexityAnalyzer.PHASES_PER_EPIC * 2L);
  }

  @Test
  void rejectsEmptyEpicList() {
    assertThatThrownBy(() -> new EpicPackage("pkg", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
