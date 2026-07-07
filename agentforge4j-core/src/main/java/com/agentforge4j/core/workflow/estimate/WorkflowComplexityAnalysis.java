// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Deterministic structural analysis of a single {@code WorkflowDefinition}, produced by
 * {@code WorkflowComplexityAnalyzer}. It carries the reproducible counts and the loop-aware
 * agent-turn attribution (minimum / expected / maximum turns, where turns inside a loop are
 * multiplied by the enclosing loop factors), the complexity classification, the deterministic
 * minimum input-token floor, and the structural risk flags. It contains execution-shape facts only
 * — never money, provider pricing, or billing.
 *
 * <p>The agent-turn triple is loop-aware: a turn outside any loop contributes once to all three;
 * a turn inside a loop contributes once to {@code minAgentTurns} (loop assumed to run once) and its
 * enclosing expected / maximum iteration products to {@code expectedAgentTurns} /
 * {@code maxAgentTurns}.
 *
 * <p><b>Branch arms are summed, not selected.</b> Exactly one {@code BRANCH} arm executes at
 * runtime, but every reachable arm's turns are added into all three figures. {@code maxAgentTurns}
 * stays a true, conservative ceiling under this convention; {@code minAgentTurns} is
 * <em>not</em> a floor for branchy workflows (an unmatched/default-less branch can complete a step
 * with zero of the summed arms actually running), and {@code expectedAgentTurns} overstates the
 * typical case in proportion to the arm count.
 *
 * @param workflowId          non-blank id of the analysed workflow
 * @param stepCount           total reachable steps (not counting loop expansion)
 * @param agentStepCount      reachable steps that invoke a model (AGENT / SPAR), not loop-expanded
 * @param branchCount         reachable {@code BRANCH} steps
 * @param loopCount           reachable loop blueprints
 * @param agentDrivenLoopCount reachable loops terminated by agent signal or evaluator
 * @param humanGateCount      reachable steps carrying a human-review / human-approval transition
 * @param maxNestingDepth     deepest structural nesting reached during traversal
 * @param minAgentTurns       loop-aware minimum agent turns (loops assumed to run once); not a true
 *                            floor for workflows containing branches (see above)
 * @param expectedAgentTurns  loop-aware expected agent turns (using expected iteration factors);
 *                            overstated for workflows containing branches (see above)
 * @param maxAgentTurns       loop-aware maximum agent turns (using maximum iteration factors)
 * @param iterationCeiling    largest enclosing loop-iteration product encountered; {@code 1} when
 *                            there are no loops. Always finite for a well-formed definition
 * @param ceilingDerivable    whether a finite execution ceiling could be derived
 * @param noCeilingReason     non-blank reason when {@code ceilingDerivable} is {@code false};
 *                            {@code null} otherwise
 * @param minimumRequiredTokens deterministic input-side floor (known step prompts + framework
 *                            overhead + mandatory structure); excludes generated output
 * @param complexityClass     the deterministic complexity classification
 * @param riskFlags           immutable, de-duplicated structural risk flags
 */
public record WorkflowComplexityAnalysis(
    String workflowId,
    int stepCount,
    int agentStepCount,
    int branchCount,
    int loopCount,
    int agentDrivenLoopCount,
    int humanGateCount,
    int maxNestingDepth,
    long minAgentTurns,
    long expectedAgentTurns,
    long maxAgentTurns,
    long iterationCeiling,
    boolean ceilingDerivable,
    String noCeilingReason,
    long minimumRequiredTokens,
    ComplexityClass complexityClass,
    List<RiskFlag> riskFlags
) {

  public WorkflowComplexityAnalysis {
    Validate.notBlank(workflowId, "workflowId must not be blank");
    Validate.isNotNegative(stepCount, "stepCount must not be negative");
    Validate.isNotNegative(agentStepCount, "agentStepCount must not be negative");
    Validate.isNotNegative(branchCount, "branchCount must not be negative");
    Validate.isNotNegative(loopCount, "loopCount must not be negative");
    Validate.isNotNegative(agentDrivenLoopCount, "agentDrivenLoopCount must not be negative");
    Validate.isNotNegative(humanGateCount, "humanGateCount must not be negative");
    Validate.isNotNegative(maxNestingDepth, "maxNestingDepth must not be negative");
    Validate.isNotNegative(minAgentTurns, "minAgentTurns must not be negative");
    Validate.isNotNegative(expectedAgentTurns, "expectedAgentTurns must not be negative");
    Validate.isNotNegative(maxAgentTurns, "maxAgentTurns must not be negative");
    Validate.isNotNegative(iterationCeiling, "iterationCeiling must not be negative");
    Validate.isNotNegative(minimumRequiredTokens, "minimumRequiredTokens must not be negative");
    Validate.notNull(complexityClass, "complexityClass must not be null");
    if (!ceilingDerivable) {
      Validate.notBlank(noCeilingReason,
          "noCeilingReason must be provided when ceilingDerivable is false");
    }
    riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
  }
}
