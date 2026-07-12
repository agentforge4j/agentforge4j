// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shared structural traversal of a {@link WorkflowDefinition} tree, used by the per-concern collectors in this package
 * (agent-ref discovery, capture-path collection) and by {@code agentforge4j-config-loader}'s workflow validation, so
 * the walking logic exists in exactly one place. Descends through branch children, retry-fallback executables,
 * blueprint bodies, and inline nested workflow definitions, but <b>not</b> through {@code workflowRef}/
 * {@code WorkflowBehaviour} boundaries — a sub-workflow referenced by id is resolved and merged separately when it
 * executes. Visits every reachable {@link StepDefinition} (including branch and fallback steps, whose
 * behaviour-specific extraction the visitor may ignore), tagged with the enclosing workflow scope.
 */
public final class WorkflowTreeWalker {

  /**
   * Default maximum nesting depth traversed before failing fast. Matches the runtime's default workflow nesting
   * limit, and turns a circular blueprint reference (a blueprint whose steps reference the blueprint itself) into a
   * clear error instead of a {@link StackOverflowError}.
   */
  public static final int MAX_TRAVERSAL_DEPTH = 32;

  private WorkflowTreeWalker() {
  }

  /**
   * Walks the tree rooted at {@code root}, invoking {@code visitor} for each reachable step with its enclosing scope.
   *
   * @param root     the root workflow to traverse; must not be {@code null}
   * @param maxDepth the maximum nesting depth to traverse before failing fast; must be greater than zero
   * @param visitor  invoked per reachable step as {@code (step, enclosingScope)}; must not be {@code null}
   *
   * @throws BlueprintStructureException if the tree nests deeper than {@code maxDepth}, or a {@link BlueprintRef}
   *                                      does not resolve to a known blueprint — both indicating a broken or
   *                                      circular blueprint reference
   */
  public static void walk(WorkflowDefinition root, int maxDepth,
      BiConsumer<StepDefinition, WorkflowDefinition> visitor) {
    Validate.notNull(root, "root must not be null");
    Validate.notNull(visitor, "visitor must not be null");
    Validate.isGreaterThanZero(maxDepth, "maxDepth must be greater than zero");
    walkSteps(root.steps(), root, maxDepth, visitor, 0);
  }

  /**
   * Validates the structural integrity of the tree rooted at {@code root} — every {@link BlueprintRef} it
   * structurally descends through resolves to a known blueprint, and no nesting chain exceeds {@code maxDepth} —
   * without invoking any per-step visitor logic. Equivalent to {@code walk(root, maxDepth, (step, scope) -> { })},
   * expressed as a dedicated method so callers don't need to know that a no-op visitor is sufficient.
   *
   * @param root     the root workflow to validate; must not be {@code null}
   * @param maxDepth the maximum nesting depth to traverse before failing fast; must be greater than zero
   *
   * @throws BlueprintStructureException if a {@link BlueprintRef} does not resolve, or nesting exceeds
   *                                      {@code maxDepth}
   */
  public static void validateStructure(WorkflowDefinition root, int maxDepth) {
    walk(root, maxDepth, (step, scope) -> { });
  }

  private static void walkSteps(List<Executable> steps, WorkflowDefinition scope, int maxDepth,
      BiConsumer<StepDefinition, WorkflowDefinition> visitor, int depth) {
    Validate.isTrue(depth <= maxDepth, () -> new BlueprintStructureException(
        "Workflow '%s' exceeds the maximum nesting depth of %s - circular blueprint reference?"
            .formatted(scope.id(), maxDepth)));
    for (Executable executable : steps) {
      walkExecutable(executable, scope, maxDepth, visitor, depth);
    }
  }

  private static void walkExecutable(Executable executable, WorkflowDefinition scope, int maxDepth,
      BiConsumer<StepDefinition, WorkflowDefinition> visitor, int depth) {
    if (executable instanceof StepDefinition step) {
      visitor.accept(step, scope);
      if (step.behaviour() instanceof BranchBehaviour branch) {
        for (Executable child : branch.childExecutables()) {
          walkExecutable(child, scope, maxDepth, visitor, depth + 1);
        }
      } else if (step.behaviour() instanceof RetryPreviousBehaviour retry && retry.fallback() != null) {
        walkExecutable(retry.fallback(), scope, maxDepth, visitor, depth + 1);
      }
    } else if (executable instanceof BlueprintRef ref) {
      BlueprintDefinition blueprint = scope.blueprints().get(ref.blueprintId());
      Validate.notNull(blueprint, () -> new BlueprintStructureException(
          "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
              .formatted(scope.id(), ref.blueprintId())));
      walkSteps(blueprint.steps(), scope, maxDepth, visitor, depth + 1);
    } else if (executable instanceof WorkflowDefinition nested) {
      walkSteps(nested.steps(), nested, maxDepth, visitor, depth + 1);
    }
  }
}
