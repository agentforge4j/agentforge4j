// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.core.workflow.WorkflowComplexityAnalyzer;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Facade over the deterministic estimation building blocks — the Java-shaped concerns a host owns
 * when driving an estimation-style workflow: turning a target definition into a structural summary
 * before the run, and, for a custom (non-catalog) workflow that does not use the {@code AGGREGATE}
 * step behaviour, aggregating the sized figures after it.
 *
 * <ol>
 *   <li>{@link #analyze(WorkflowDefinition)} (Mode 1) or {@link #analyze(EpicPackage)} (Mode 2) runs
 *       the appropriate deterministic analyzer to produce a {@link WorkflowComplexityAnalysis} —
 *       both modes converge on the same analysis shape, so the rest of the pipeline is mode-agnostic.
 *       </li>
 *   <li>{@link #summarize(WorkflowComplexityAnalysis, ObjectMapper)} serialises that analysis to a
 *       compact JSON structural summary, which the host supplies to the run as an {@code INPUT}
 *       answer — the workflow never receives the {@code WorkflowDefinition} or {@code EpicPackage}
 *       object itself.</li>
 *   <li>{@link #aggregate(WorkflowComplexityAnalysis, SizingInputs)} combines the analysis with the
 *       per-turn sizing produced by an execution-sizing agent into the final
 *       {@link ExecutionEstimate}. The shipped {@code workflow-execution-estimator} catalog bundle
 *       no longer calls this host-side: it aggregates in-workflow via the {@code AGGREGATE} step
 *       behaviour and the built-in {@code workflow-execution-estimate} {@code ContextAggregator},
 *       which wraps {@link WorkflowExecutionAggregator#aggregate} directly. This method remains for
 *       a custom, non-catalog workflow that drives aggregation from host Java instead.</li>
 * </ol>
 *
 * <p>This facade holds no run state and performs no run orchestration; starting the workflow,
 * collecting the sized figures, and reading them back are the caller's concern (the embedding
 * application, the SDLC workflow, or an example harness).
 */
public final class WorkflowExecutionAnalysisService {

  private WorkflowExecutionAnalysisService() {
  }

  /**
   * Analyses a target workflow's structure.
   *
   * @param definition the target workflow; must not be {@code null}
   *
   * @return the deterministic structural analysis; never {@code null}
   */
  public static WorkflowComplexityAnalysis analyze(WorkflowDefinition definition) {
    Validate.notNull(definition, "definition must not be null");
    return WorkflowComplexityAnalyzer.analyze(definition);
  }

  /**
   * Analyses an epic package's structure (Mode 2 — SDLC / epic-package estimation).
   *
   * @param epicPackage the target epic package; must not be {@code null}
   *
   * @return the deterministic structural analysis; never {@code null}
   */
  public static WorkflowComplexityAnalysis analyze(EpicPackage epicPackage) {
    Validate.notNull(epicPackage, "epicPackage must not be null");
    return EpicPackageComplexityAnalyzer.analyze(epicPackage);
  }

  /**
   * Serialises a structural analysis to a compact JSON summary for use as a run input answer.
   *
   * @param analysis the analysis to summarise; must not be {@code null}
   * @param mapper   the caller's Jackson mapper; must not be {@code null}
   *
   * @return the JSON structural summary; never {@code null}
   */
  public static String summarize(WorkflowComplexityAnalysis analysis, ObjectMapper mapper) {
    Validate.notNull(analysis, "analysis must not be null");
    Validate.notNull(mapper, "mapper must not be null");
    try {
      return mapper.writeValueAsString(analysis);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise workflow complexity analysis", e);
    }
  }

  /**
   * Aggregates a structural analysis and the per-turn sizing into the final execution estimate.
   *
   * @param analysis the structural analysis; must not be {@code null}
   * @param sizing   the per-turn sizing magnitudes; must not be {@code null}
   *
   * @return the neutral execution estimate; never {@code null}
   */
  public static ExecutionEstimate aggregate(WorkflowComplexityAnalysis analysis, SizingInputs sizing) {
    return WorkflowExecutionAggregator.aggregate(analysis, sizing);
  }
}
