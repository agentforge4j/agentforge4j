// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.core.workflow.WorkflowComplexityAnalyzer;
import com.agentforge4j.util.Validate;
import java.util.EnumSet;
import java.util.List;

/**
 * Deterministic structural analysis of an {@link EpicPackage} for Mode 2 (SDLC / epic-package)
 * estimation, producing the same {@link WorkflowComplexityAnalysis} shape
 * {@link WorkflowComplexityAnalyzer} produces for Mode 1 — so both modes compose through the same
 * {@link WorkflowExecutionAggregator}.
 *
 * <p><b>Provisional assumption, flagged prominently.</b> The Full Application SDLC workflow this
 * models does not exist yet (a separate, later workstream). Absent a real workflow definition to
 * walk, this analyzer assumes a fixed per-epic phase pipeline and a fixed per-phase rework-iteration
 * ceiling, documented below as tunable constants. These will need reconciling against the real SDLC
 * workflow's actual blueprint {@code maxIterations} once it exists — this analyzer is a best current
 * estimate of a not-yet-built system's shape, not a derivation from its live structure.
 *
 * <p>Per epic, the assumed pipeline is four phases — Architecture, Test Strategy, Development,
 * Security &amp; Delivery Readiness — each modelled as an evaluator-gated rework loop (one review
 * turn per iteration). The package as a whole adds one final aggregate phase (Application Delivery
 * Package) not repeated per epic.
 *
 * <p><b>{@link RiskFlag#HIGH_ITERATION_CEILING} never applies to Mode 2.</b> Every epic's
 * {@link Epic#expectedReworkIterations()} hint is capped at
 * {@link #DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE} (see {@link #analyze(EpicPackage)}), so the
 * iteration ceiling this analyzer reports is always that fixed default — no input can raise it. The
 * default is always far below the threshold {@link RiskFlag#HIGH_ITERATION_CEILING} requires
 * (mirroring Mode 1's own "large iteration expansion" contract), so the flag is structurally
 * unreachable for this analyzer and is not evaluated at all, rather than left as a conditional that
 * can never be true. {@link #HIGH_RISK_MIN_EPICS} / {@link RiskFlag#LARGE_STRUCTURE} is Mode 2's
 * sole risk signal beyond {@link RiskFlag#AGENT_DRIVEN_LOOP}.
 */
public final class EpicPackageComplexityAnalyzer {

  /** Assumed SDLC phases repeated per epic (Architecture, Test Strategy, Development, Security). */
  public static final int PHASES_PER_EPIC = 4;

  /** Assumed final aggregate phase, once per package, not repeated per epic. */
  private static final int FINAL_PACKAGE_PHASES = 1;

  /**
   * Assumed per-phase rework-iteration ceiling absent a real SDLC workflow to read
   * {@code maxIterations} from. Provisional — see class Javadoc.
   */
  public static final int DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE = 3;

  /** Fixed per-phase framework input overhead for the deterministic minimum-token floor. */
  private static final long FRAMEWORK_OVERHEAD_TOKENS_PER_PHASE = 200L;

  /** Fixed base input cost for mandatory run structure. */
  private static final long BASE_STRUCTURE_TOKENS = 100L;

  /** Epic count at or above which a package is treated as high risk (very large but bounded). */
  private static final int HIGH_RISK_MIN_EPICS = 11;

  /** Epic count at or above which a package is at least complex. */
  private static final int COMPLEX_MIN_EPICS = 6;

  /** Epic count at or above which a package is at least moderate. */
  private static final int MODERATE_MIN_EPICS = 3;

  private EpicPackageComplexityAnalyzer() {
  }

  /**
   * Analyses the structure of an epic package.
   *
   * @param epicPackage the package to analyse; must not be {@code null}
   *
   * @return the deterministic structural analysis; never {@code null}
   */
  public static WorkflowComplexityAnalysis analyze(EpicPackage epicPackage) {
    Validate.notNull(epicPackage, "epicPackage must not be null");

    int epicCount = epicPackage.epics().size();
    int totalPhases = epicCount * PHASES_PER_EPIC + FINAL_PACKAGE_PHASES;
    int loopCount = epicCount * PHASES_PER_EPIC;

    // One review turn per phase if every rework loop runs once; the final aggregate phase is not a
    // rework loop, so it always contributes exactly one turn in every case (min/expected/max).
    long minTurns = totalPhases;
    long expectedTurns = FINAL_PACKAGE_PHASES;
    long maxTurns = (long) epicCount * PHASES_PER_EPIC * DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE
        + FINAL_PACKAGE_PHASES;
    for (Epic epic : epicPackage.epics()) {
      // The hint is advisory and capped at the per-phase rework ceiling: expected turns feed the
      // expected token figure, which must stay within the [min, max] envelope, and maxTurns above
      // is derived from the same ceiling. A hint at or above the ceiling therefore means
      // "expect the worst case", not a wider envelope.
      long expectedIterations = epic.expectedReworkIterations() != null
          ? Math.min(epic.expectedReworkIterations(), DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE)
          : Math.max(1, (DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE + 1) / 2);
      expectedTurns += (long) PHASES_PER_EPIC * expectedIterations;
    }

    long minimumRequiredTokens = (long) totalPhases * FRAMEWORK_OVERHEAD_TOKENS_PER_PHASE
        + BASE_STRUCTURE_TOKENS;

    ComplexityClass complexityClass = classify(epicCount);
    List<RiskFlag> riskFlags = riskFlags(epicCount);

    return new WorkflowComplexityAnalysis(
        epicPackage.packageId(),
        totalPhases,
        totalPhases,
        0,
        loopCount,
        loopCount,
        0,
        1,
        minTurns,
        expectedTurns,
        maxTurns,
        DEFAULT_MAX_REWORK_ITERATIONS_PER_PHASE,
        true,
        null,
        minimumRequiredTokens,
        complexityClass,
        riskFlags);
  }

  private static ComplexityClass classify(int epicCount) {
    if (epicCount >= HIGH_RISK_MIN_EPICS) {
      return ComplexityClass.HIGH_RISK;
    }
    if (epicCount >= COMPLEX_MIN_EPICS) {
      return ComplexityClass.COMPLEX;
    }
    if (epicCount >= MODERATE_MIN_EPICS) {
      return ComplexityClass.MODERATE;
    }
    return ComplexityClass.SIMPLE;
  }

  /**
   * {@link RiskFlag#HIGH_ITERATION_CEILING} is deliberately never evaluated here — see the class
   * Javadoc for why it is structurally unreachable under this analyzer's capped-hint model.
   */
  private static List<RiskFlag> riskFlags(int epicCount) {
    EnumSet<RiskFlag> flags = EnumSet.noneOf(RiskFlag.class);
    flags.add(RiskFlag.AGENT_DRIVEN_LOOP);
    if (epicCount >= HIGH_RISK_MIN_EPICS) {
      flags.add(RiskFlag.LARGE_STRUCTURE);
    }
    return List.copyOf(flags);
  }
}
