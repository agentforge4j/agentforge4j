// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.util.Validate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects the union of {@code requiredArtifacts} declared by every {@code VALIDATE} step reachable <em>within a single
 * {@link WorkflowDefinition}</em> — descending through branch targets, predicate targets, blueprint bodies, and inline
 * nested definitions, but <b>not</b> through {@code workflowRef}/{@code WorkflowBehaviour} boundaries. Sub-workflows are
 * merged into the run-level capture set separately when they execute, so this collector deliberately stops at the
 * {@code workflowRef} edge (mirroring {@link WorkflowAgentRefCollector}, which likewise does not follow it).
 *
 * <p>The result feeds {@code WorkflowState.mergeCapturedArtifactPaths}, scoping in-process artifact capture to exactly
 * the paths a {@code VALIDATE} step needs: a workflow with no reachable {@code VALIDATE} step captures nothing.
 */
public final class WorkflowCapturePathCollector {

  /**
   * Maximum nesting depth traversed before failing fast. Mirrors {@link WorkflowTreeWalker#MAX_TRAVERSAL_DEPTH}: a
   * circular blueprint reference fails with a clear error instead of a {@link StackOverflowError}.
   */
  public static final int MAX_TRAVERSAL_DEPTH = WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH;

  private WorkflowCapturePathCollector() {
  }

  /**
   * Collects the capture-eligible artifact paths declared by the {@code VALIDATE} steps reachable within one workflow
   * definition.
   *
   * @param definition the workflow definition to traverse; must not be {@code null}
   *
   * @return immutable set of declared {@code requiredArtifacts} paths (empty when no {@code VALIDATE} step is reachable)
   *
   * @throws BlueprintStructureException if the tree nests deeper than {@link #MAX_TRAVERSAL_DEPTH}, or a blueprint
   *                                      reference does not resolve — both indicating a broken or circular blueprint
   *                                      reference
   */
  public static Set<String> collect(WorkflowDefinition definition) {
    Validate.notNull(definition, "definition must not be null");
    Set<String> paths = new LinkedHashSet<>();
    WorkflowTreeWalker.walk(definition, MAX_TRAVERSAL_DEPTH, (step, scope) -> {
      if (step.behaviour() instanceof ValidateBehaviour validate) {
        paths.addAll(validate.requiredArtifacts());
      }
    });
    return Set.copyOf(paths);
  }
}
