// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks the reachable step graph of a workflow, exactly as the runtime resolves a step id at a gate or input-submission
 * point. Single source of truth for "which step ids are reachable from a root, and at which structural locations" —
 * consumed both by the runtime (to resolve the current step) and by the config loader (to reject ambiguous reachable
 * step ids before a run can hit them).
 *
 * <p>Descent rules (matching the runtime's step resolution):
 * <ul>
 *   <li>a {@link StepDefinition} contributes a location, and a {@code WORKFLOW}-behaviour step
 *       descends into the resolved sub-workflow;</li>
 *   <li>a {@link BlueprintRef} descends into the referenced blueprint;</li>
 *   <li>an inline {@link WorkflowDefinition} descends into its steps;</li>
 *   <li>an inline {@link BlueprintDefinition} is <em>not</em> descended — it is not directly
 *       executable, so its steps are unreachable at runtime.</li>
 * </ul>
 *
 * <p>Locations are deduplicated by their defining-container path: a single definition reached by more
 * than one path to the same container collapses to one location, while the same step id defined at
 * two different structural locations stays distinct (and therefore ambiguous).
 *
 * <p>Cycle safety is by visited sets, not a depth bound, so an acyclic graph of any depth is walked
 * in full while a cyclic graph terminates: sub-workflows are entered at most once (a global
 * workflow-id visited set), and a blueprint reference already on the current descent chain within a
 * workflow frame is skipped.
 */
public final class ReachableStepGraph {

  private ReachableStepGraph() {
  }

  /**
   * Walks every reachable step occurrence from {@code root}.
   *
   * @param root     the root workflow to walk from
   * @param resolver resolves {@code WORKFLOW}-step refs to their definitions ({@code null} when absent)
   *
   * @return every reachable step occurrence, deduplicated by structural location
   */
  public static List<ReachableStep> walk(WorkflowDefinition root, WorkflowRefResolver resolver) {
    Validate.notNull(root, "root must not be null");
    Validate.notNull(resolver, "resolver must not be null");
    Traversal traversal = new Traversal(resolver);
    traversal.enterWorkflow(root, "wf:" + root.id(), new HashSet<>());
    return List.copyOf(traversal.byLocation.values());
  }

  /**
   * Resolves {@code stepId} to its single reachable step, mirroring the runtime's fail-closed resolution.
   *
   * @param root     the root workflow to resolve from
   * @param stepId   the step id to resolve
   * @param resolver resolves {@code WORKFLOW}-step refs to their definitions ({@code null} when absent)
   *
   * @return the matching step, or {@code null} when none is reachable
   *
   * @throws IllegalStateException when {@code stepId} is reachable from more than one structural location
   */
  public static StepDefinition resolveUnique(WorkflowDefinition root, String stepId,
      WorkflowRefResolver resolver) {
    ReachableStep resolved = resolveUniqueOccurrence(root, stepId, resolver);
    return resolved == null ? null : resolved.step();
  }

  /**
   * Resolves {@code stepId} to its single reachable occurrence — the step plus the workflow that
   * declares it — mirroring the runtime's fail-closed resolution. Use this over {@link #resolveUnique}
   * when the declaring workflow is also needed (for example, to resolve a {@code STEP_ACTION}
   * requirement against the workflow that actually declares the step, not necessarily the root).
   *
   * @param root     the root workflow to resolve from
   * @param stepId   the step id to resolve
   * @param resolver resolves {@code WORKFLOW}-step refs to their definitions ({@code null} when absent)
   *
   * @return the matching occurrence, or {@code null} when none is reachable
   *
   * @throws IllegalStateException when {@code stepId} is reachable from more than one structural location
   */
  public static ReachableStep resolveUniqueOccurrence(WorkflowDefinition root, String stepId,
      WorkflowRefResolver resolver) {
    Validate.notBlank(stepId, "stepId must not be blank");
    List<ReachableStep> matches = new ArrayList<>();
    for (ReachableStep reachable : walk(root, resolver)) {
      if (reachable.stepId().equals(stepId)) {
        matches.add(reachable);
      }
    }
    if (matches.isEmpty()) {
      return null;
    }
    int matchCount = matches.size();
    Validate.isTrue(matchCount == 1, () -> new IllegalStateException(
        ("Ambiguous step id '%s' resolves to %d structural locations across the reachable workflow "
            + "graph from workflow '%s'; reachable step ids must be unique")
            .formatted(stepId, matchCount, root.id())));
    return matches.get(0);
  }

  /**
   * Finds every step id reachable from {@code root} at two or more structural locations.
   *
   * @param root     the root workflow to walk from
   * @param resolver resolves {@code WORKFLOW}-step refs to their definitions ({@code null} when absent)
   *
   * @return the ambiguous step ids with their conflicting locations, in encounter order; empty when none
   */
  public static List<AmbiguousStepId> findAmbiguousStepIds(WorkflowDefinition root,
      WorkflowRefResolver resolver) {
    Map<String, List<String>> locationsByStepId = new LinkedHashMap<>();
    for (ReachableStep reachable : walk(root, resolver)) {
      locationsByStepId.computeIfAbsent(reachable.stepId(), key -> new ArrayList<>()).add(reachable.location());
    }
    List<AmbiguousStepId> ambiguous = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : locationsByStepId.entrySet()) {
      if (entry.getValue().size() > 1) {
        ambiguous.add(new AmbiguousStepId(entry.getKey(), entry.getValue()));
      }
    }
    return List.copyOf(ambiguous);
  }

  /**
   * Mutable traversal state for a single walk: the resolver, the global workflow-id visited set, and the by-location
   * accumulator. The blueprint-ref path guard is passed per workflow frame.
   */
  private static final class Traversal {

    private final WorkflowRefResolver resolver;
    private final Set<String> visitedWorkflowIds = new HashSet<>();
    private final Map<String, ReachableStep> byLocation = new LinkedHashMap<>();

    private Traversal(WorkflowRefResolver resolver) {
      this.resolver = resolver;
    }

    private void enterWorkflow(WorkflowDefinition workflow, String containerKey,
        Set<String> blueprintPath) {
      if (!visitedWorkflowIds.add(workflow.id())) {
        return;
      }
      collect(workflow.steps(), workflow, containerKey, blueprintPath);
    }

    private void collect(List<Executable> executables, WorkflowDefinition enclosing,
        String containerKey, Set<String> blueprintPath) {
      for (Executable executable : executables) {
        if (executable instanceof StepDefinition step) {
          recordStep(step, enclosing, containerKey);
          if (step.behaviour() instanceof WorkflowBehaviour workflowBehaviour) {
            WorkflowDefinition sub = resolver.resolve(workflowBehaviour.workflowRef());
            if (sub != null) {
              enterWorkflow(sub, "wf:" + sub.id(), new HashSet<>());
            }
          }
        } else if (executable instanceof BlueprintRef ref) {
          BlueprintDefinition blueprint = enclosing.blueprints().get(ref.blueprintId());
          // Skip a blueprint already on the current descent chain (a blueprint-ref cycle); for an
          // acyclic graph the id is never already present, so this never alters a valid walk.
          if (blueprint != null && blueprintPath.add(ref.blueprintId())) {
            collect(blueprint.steps(), enclosing, containerKey + "/bp:" + ref.blueprintId(),
                blueprintPath);
            blueprintPath.remove(ref.blueprintId());
          }
        } else if (executable instanceof WorkflowDefinition nested) {
          collect(nested.steps(), nested, "wf:" + nested.id(), new HashSet<>());
        }
      }
    }

    private void recordStep(StepDefinition step, WorkflowDefinition declaringWorkflow, String containerKey) {
      String location = containerKey + "/step:" + step.stepId();
      byLocation.putIfAbsent(location, new ReachableStep(step.stepId(), location, step, declaringWorkflow));
    }
  }
}
