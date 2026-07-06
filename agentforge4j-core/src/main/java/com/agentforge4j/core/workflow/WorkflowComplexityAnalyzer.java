// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.estimate.ComplexityClass;
import com.agentforge4j.core.workflow.estimate.RiskFlag;
import com.agentforge4j.core.workflow.estimate.WorkflowComplexityAnalysis;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.TransitionAware;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.core.workflow.step.loop.LoopConfig;
import com.agentforge4j.core.workflow.step.loop.LoopTerminationStrategy;
import com.agentforge4j.util.Validate;
import java.util.EnumSet;
import java.util.List;

/**
 * Deterministic structural analysis of a single {@link WorkflowDefinition} for execution
 * estimation. Produces a {@link WorkflowComplexityAnalysis}: reproducible counts, loop-aware
 * agent-turn attribution, a complexity classification, the minimum input-token floor, and
 * structural risk flags — execution shape only, never money or provider cost.
 *
 * <p><b>Traversal.</b> Unlike the flat per-concern collectors in this package (which delegate to the
 * shared {@code WorkflowTreeWalker}), this analyzer performs its own bounded recursive descent
 * because it must carry loop-expansion factors and nesting depth down the tree — context the shared
 * walker's {@code (step, scope)} visitor signature does not expose. The descent mirrors the shared
 * walker's rules exactly: it follows branch children, blueprint bodies, and inline nested
 * definitions, stops at {@code workflowRef}/{@code WorkflowBehaviour} boundaries, and fails fast at
 * {@link WorkflowTreeWalker#MAX_TRAVERSAL_DEPTH} to turn a circular blueprint reference into a clear
 * error instead of a {@link StackOverflowError}.
 *
 * <p><b>Classification thresholds, the expected-iteration defaults, and the token-floor constants
 * below are deterministic implementation choices, intended to be tunable.</b> They are not part of
 * any published contract.
 */
public final class WorkflowComplexityAnalyzer {

  /** Rough characters-per-token divisor for the deterministic input floor. */
  private static final int CHARS_PER_TOKEN = 4;

  /** Fixed per-agent-step framework input overhead (system framing, tool schemas, etc.). */
  private static final long FRAMEWORK_OVERHEAD_TOKENS_PER_AGENT_STEP = 200L;

  /** Fixed base input cost for mandatory run structure. */
  private static final long BASE_STRUCTURE_TOKENS = 100L;

  /** Saturating cap for iteration-factor products so deep loop nesting cannot overflow a long. */
  private static final long ITERATION_FACTOR_CAP = 1_000_000L;

  /** Iteration ceiling at or above which a workload is treated as high risk. */
  private static final long HIGH_RISK_ITERATION_CEILING = 50L;

  /** Maximum-case agent turns at or above which a workload is treated as high risk. */
  private static final long HIGH_RISK_MAX_TURNS = 60L;

  /** Agent-step count at or above which a bounded workload is at least complex. */
  private static final int COMPLEX_MIN_AGENT_STEPS = 9;

  /** Branch count at or above which a bounded workload is at least complex. */
  private static final int COMPLEX_MIN_BRANCHES = 3;

  /** Agent-step count at or above which a small workload is at least moderate. */
  private static final int MODERATE_MIN_AGENT_STEPS = 4;

  /** Nesting depth at or above which {@link RiskFlag#DEEP_NESTING} is raised. */
  private static final int DEEP_NESTING_THRESHOLD = 4;

  /** Step count at or above which {@link RiskFlag#LARGE_STRUCTURE} is raised. */
  private static final int LARGE_STRUCTURE_STEPS = 20;

  private WorkflowComplexityAnalyzer() {
  }

  /**
   * Analyses the structure of a workflow definition.
   *
   * @param root the workflow to analyse; must not be {@code null}
   *
   * @return the deterministic structural analysis; never {@code null}
   *
   * @throws IllegalArgumentException if {@code root} is {@code null}, or the tree nests deeper than
   *                                  {@link WorkflowTreeWalker#MAX_TRAVERSAL_DEPTH} (a circular
   *                                  blueprint reference)
   */
  public static WorkflowComplexityAnalysis analyze(WorkflowDefinition root) {
    Validate.notNull(root, "root must not be null");
    Accumulator acc = new Accumulator();
    walk(root.steps(), root, 1L, 1L, 0, acc);

    long minimumRequiredTokens = (long) acc.knownPromptChars / CHARS_PER_TOKEN
        + (long) acc.agentStepCount * FRAMEWORK_OVERHEAD_TOKENS_PER_AGENT_STEP
        + BASE_STRUCTURE_TOKENS;

    ComplexityClass complexityClass = classify(acc);
    List<RiskFlag> riskFlags = riskFlags(acc);

    return new WorkflowComplexityAnalysis(
        root.id(),
        acc.stepCount,
        acc.agentStepCount,
        acc.branchCount,
        acc.loopCount,
        acc.agentDrivenLoopCount,
        acc.humanGateCount,
        acc.maxNestingDepth,
        acc.minTurns,
        acc.expectedTurns,
        acc.maxTurns,
        acc.iterationCeiling,
        true,
        null,
        minimumRequiredTokens,
        complexityClass,
        riskFlags);
  }

  private static void walk(List<Executable> steps, WorkflowDefinition scope,
      long expectedFactor, long maxFactor, int depth, Accumulator acc) {
    Validate.isTrue(depth <= WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH,
        "Workflow '%s' exceeds the maximum nesting depth of %s - circular blueprint reference?"
            .formatted(scope.id(), WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH));
    acc.maxNestingDepth = Math.max(acc.maxNestingDepth, depth);
    for (Executable executable : steps) {
      walkExecutable(executable, scope, expectedFactor, maxFactor, depth, acc);
    }
  }

  private static void walkExecutable(Executable executable, WorkflowDefinition scope,
      long expectedFactor, long maxFactor, int depth, Accumulator acc) {
    if (executable instanceof StepDefinition step) {
      visitStep(step, expectedFactor, maxFactor, acc);
      if (step.behaviour() instanceof BranchBehaviour branch) {
        acc.branchCount++;
        for (Executable child : branch.childExecutables()) {
          walkExecutable(child, scope, expectedFactor, maxFactor, depth + 1, acc);
        }
      }
    } else if (executable instanceof BlueprintRef ref) {
      BlueprintDefinition blueprint = scope.blueprints().get(ref.blueprintId());
      Validate.notNull(blueprint,
          "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
              .formatted(scope.id(), ref.blueprintId()));
      LoopConfig loop = blueprint.behaviour() != null ? blueprint.behaviour().loopConfig() : null;
      long childExpected = expectedFactor;
      long childMax = maxFactor;
      if (loop != null) {
        acc.loopCount++;
        LoopTerminationStrategy strategy = loop.terminationStrategy();
        if (strategy == LoopTerminationStrategy.AGENT_SIGNAL
            || strategy == LoopTerminationStrategy.EVALUATOR) {
          acc.agentDrivenLoopCount++;
        }
        long expectedIterations = expectedIterations(loop, strategy);
        childExpected = saturatingMultiply(expectedFactor, expectedIterations);
        childMax = saturatingMultiply(maxFactor, loop.maxIterations());
        acc.iterationCeiling = Math.max(acc.iterationCeiling, childMax);
      }
      walk(blueprint.steps(), scope, childExpected, childMax, depth + 1, acc);
    } else if (executable instanceof WorkflowDefinition nested) {
      walk(nested.steps(), nested, expectedFactor, maxFactor, depth + 1, acc);
    }
  }

  private static void visitStep(StepDefinition step, long expectedFactor, long maxFactor,
      Accumulator acc) {
    acc.stepCount++;
    String prompt = step.stepPrompt();
    if (prompt != null && !prompt.isBlank()) {
      acc.knownPromptChars += prompt.length();
    }
    StepBehaviour behaviour = step.behaviour();
    if (behaviour instanceof TransitionAware transitionAware) {
      StepTransition transition = transitionAware.transition();
      if (transition == StepTransition.HUMAN_REVIEW || transition == StepTransition.HUMAN_APPROVAL) {
        acc.humanGateCount++;
      }
    }
    int turnsForStep = agentTurnsForStep(behaviour);
    if (turnsForStep > 0) {
      acc.agentStepCount++;
      acc.minTurns += turnsForStep;
      acc.expectedTurns += saturatingMultiply(turnsForStep, expectedFactor);
      acc.maxTurns += saturatingMultiply(turnsForStep, maxFactor);
    }
  }

  /**
   * Model turns a single step contributes: an {@code AGENT} step is one turn; a {@code SPAR} step is
   * two (primary plus challenger). All other behaviours contribute none.
   */
  private static int agentTurnsForStep(StepBehaviour behaviour) {
    if (behaviour instanceof AgentBehaviour) {
      return 1;
    }
    if (behaviour instanceof SparBehaviour) {
      return 2;
    }
    return 0;
  }

  /**
   * Expected-case iterations for a loop: the declared {@code expectedIterations} hint when present;
   * otherwise the full {@code maxIterations} for fixed-count and for-each loops, or half (rounded
   * up, at least one) for agent-signal / evaluator loops whose real count is model-decided.
   */
  private static long expectedIterations(LoopConfig loop, LoopTerminationStrategy strategy) {
    Integer hint = loop.expectedIterations();
    if (hint != null) {
      return hint;
    }
    int max = loop.maxIterations();
    if (strategy == LoopTerminationStrategy.AGENT_SIGNAL
        || strategy == LoopTerminationStrategy.EVALUATOR) {
      return Math.max(1, (max + 1) / 2);
    }
    return max;
  }

  private static ComplexityClass classify(Accumulator acc) {
    if (acc.agentDrivenLoopCount > 0
        || acc.iterationCeiling >= HIGH_RISK_ITERATION_CEILING
        || acc.maxTurns >= HIGH_RISK_MAX_TURNS) {
      return ComplexityClass.HIGH_RISK;
    }
    if (acc.loopCount > 0
        || acc.branchCount >= COMPLEX_MIN_BRANCHES
        || acc.agentStepCount >= COMPLEX_MIN_AGENT_STEPS) {
      return ComplexityClass.COMPLEX;
    }
    if (acc.agentStepCount >= MODERATE_MIN_AGENT_STEPS || acc.branchCount >= 1) {
      return ComplexityClass.MODERATE;
    }
    return ComplexityClass.SIMPLE;
  }

  private static List<RiskFlag> riskFlags(Accumulator acc) {
    EnumSet<RiskFlag> flags = EnumSet.noneOf(RiskFlag.class);
    if (acc.agentDrivenLoopCount > 0) {
      flags.add(RiskFlag.AGENT_DRIVEN_LOOP);
    }
    if (acc.iterationCeiling >= HIGH_RISK_ITERATION_CEILING) {
      flags.add(RiskFlag.HIGH_ITERATION_CEILING);
    }
    if (acc.branchCount > 0) {
      flags.add(RiskFlag.LLM_DECIDED_BRANCHING);
    }
    if (acc.maxNestingDepth >= DEEP_NESTING_THRESHOLD) {
      flags.add(RiskFlag.DEEP_NESTING);
    }
    if (acc.stepCount >= LARGE_STRUCTURE_STEPS) {
      flags.add(RiskFlag.LARGE_STRUCTURE);
    }
    return List.copyOf(flags);
  }

  private static long saturatingMultiply(long a, long b) {
    long product = a * b;
    if (b != 0 && (product / b != a || product > ITERATION_FACTOR_CAP)) {
      return ITERATION_FACTOR_CAP;
    }
    return Math.min(product, ITERATION_FACTOR_CAP);
  }

  private static final class Accumulator {
    private int stepCount;
    private int agentStepCount;
    private int branchCount;
    private int loopCount;
    private int agentDrivenLoopCount;
    private int humanGateCount;
    private int maxNestingDepth;
    private long minTurns;
    private long expectedTurns;
    private long maxTurns;
    private long iterationCeiling = 1L;
    private long knownPromptChars;
  }
}
