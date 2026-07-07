// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.core.workflow.WorkflowComplexityAnalyzer;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Facade over the deterministic estimation building blocks — the two genuinely Java-shaped concerns
 * a host owns when driving the {@code workflow-execution-estimator} workflow: turning a target
 * definition into a structural summary before the run, and aggregating the sized figures after it.
 *
 * <ol>
 *   <li>{@link #analyze(WorkflowDefinition)} runs {@link WorkflowComplexityAnalyzer} to produce the
 *       deterministic {@link WorkflowComplexityAnalysis}.</li>
 *   <li>{@link #summarize(WorkflowComplexityAnalysis)} serialises that analysis to a compact JSON
 *       structural summary, which the host supplies to the run as an {@code INPUT} answer — the
 *       workflow never receives the {@code WorkflowDefinition} object itself.</li>
 *   <li>{@link #aggregate(WorkflowComplexityAnalysis, SizingInputs)} combines the analysis with the
 *       per-turn sizing the {@code execution-estimator} agent produced into the final
 *       {@link ExecutionEstimate}.</li>
 * </ol>
 *
 * <p>This facade holds no run state and performs no run orchestration; starting the workflow,
 * collecting the sized figures, and reading them back are the caller's concern (the embedding
 * application, the SDLC workflow, or an example harness).
 */
public final class WorkflowExecutionAnalysisService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
   * Serialises a structural analysis to a compact JSON summary for use as a run input answer.
   *
   * @param analysis the analysis to summarise; must not be {@code null}
   *
   * @return the JSON structural summary; never {@code null}
   */
  public static String summarize(WorkflowComplexityAnalysis analysis) {
    Validate.notNull(analysis, "analysis must not be null");
    try {
      return MAPPER.writeValueAsString(analysis);
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
