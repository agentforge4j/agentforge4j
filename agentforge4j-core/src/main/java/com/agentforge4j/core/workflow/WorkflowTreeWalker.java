// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shared structural traversal of a {@link WorkflowDefinition} tree, used by the per-concern collectors in this package
 * (agent-ref discovery, capture-path collection) so the walking logic exists in exactly one place. Descends through
 * branch children, blueprint bodies, and inline nested workflow definitions, but <b>not</b> through
 * {@code workflowRef}/{@code WorkflowBehaviour} boundaries — a sub-workflow referenced by id is resolved and merged
 * separately when it executes. Visits every reachable {@link StepDefinition} (including branch steps, whose
 * behaviour-specific extraction the visitor may ignore), tagged with the enclosing workflow scope.
 */
final class WorkflowTreeWalker {

  /**
   * Maximum nesting depth traversed before failing fast. Matches the runtime's default workflow nesting limit, and
   * turns a circular blueprint reference (a blueprint whose steps reference the blueprint itself) into a clear error
   * instead of a {@link StackOverflowError}.
   */
  static final int MAX_TRAVERSAL_DEPTH = 32;

  private WorkflowTreeWalker() {
  }

  /**
   * Walks the tree rooted at {@code root}, invoking {@code visitor} for each reachable step with its enclosing scope.
   *
   * @param root    the root workflow to traverse; must not be {@code null}
   * @param visitor invoked per reachable step as {@code (step, enclosingScope)}; must not be {@code null}
   *
   * @throws IllegalArgumentException if the tree nests deeper than {@link #MAX_TRAVERSAL_DEPTH}, which indicates a
   *                                  circular blueprint reference
   */
  static void walk(WorkflowDefinition root, BiConsumer<StepDefinition, WorkflowDefinition> visitor) {
    Validate.notNull(root, "root must not be null");
    Validate.notNull(visitor, "visitor must not be null");
    walkSteps(root.steps(), root, visitor, 0);
  }

  private static void walkSteps(List<Executable> steps, WorkflowDefinition scope,
      BiConsumer<StepDefinition, WorkflowDefinition> visitor, int depth) {
    Validate.isTrue(depth <= MAX_TRAVERSAL_DEPTH,
        "Workflow '%s' exceeds the maximum nesting depth of %s - circular blueprint reference?"
            .formatted(scope.id(), MAX_TRAVERSAL_DEPTH));
    for (Executable executable : steps) {
      walkExecutable(executable, scope, visitor, depth);
    }
  }

  private static void walkExecutable(Executable executable, WorkflowDefinition scope,
      BiConsumer<StepDefinition, WorkflowDefinition> visitor, int depth) {
    if (executable instanceof StepDefinition step) {
      visitor.accept(step, scope);
      if (step.behaviour() instanceof BranchBehaviour branch) {
        for (Executable child : branch.childExecutables()) {
          walkExecutable(child, scope, visitor, depth + 1);
        }
      }
    } else if (executable instanceof BlueprintRef ref) {
      BlueprintDefinition blueprint = scope.blueprints().get(ref.blueprintId());
      Validate.notNull(blueprint,
          "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
              .formatted(scope.id(), ref.blueprintId()));
      walkSteps(blueprint.steps(), scope, visitor, depth + 1);
    } else if (executable instanceof WorkflowDefinition nested) {
      walkSteps(nested.steps(), nested, visitor, depth + 1);
    }
  }
}
