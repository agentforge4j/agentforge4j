// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
 *   <li>a {@code BranchBehaviour} step descends into its routable children (exact-match, predicate,
 *       and default targets) in the same container;</li>
 *   <li>a {@link BlueprintRef} descends into the referenced blueprint;</li>
 *   <li>an inline {@link WorkflowDefinition} descends into its steps;</li>
 *   <li>an inline {@link BlueprintDefinition} is <em>not</em> descended — it is not directly
 *       executable, so its steps are unreachable at runtime.</li>
 * </ul>
 *
 * <p>Locations are deduplicated by their defining-container path <em>and definition identity</em>: a
 * single definition reached by more than one path to the same container collapses to one location,
 * while the same step id defined at two different structural locations — or by two distinct
 * definitions at the same container path (e.g. two branch arms each defining a step with the same
 * id) — stays distinct (and therefore ambiguous).
 *
 * <p>Cycle safety is by visited sets: sub-workflows are entered at most once (a global workflow-id
 * visited set), and a blueprint reference already on the current descent chain within a workflow
 * frame is skipped. Fully explored blueprint subtrees are additionally memoized per walk and
 * replayed (re-prefixed) at later reference sites, so repeated references — including diamond-shaped
 * reference chains — cost linear work instead of re-walking the subtree per reference. As a
 * fail-closed backstop against pathological bundles, a walk that exceeds the maximum reference depth
 * or the maximum traversal size throws {@link IllegalStateException} instead of exhausting the stack
 * or the CPU; both consumers (the runtime's step resolution and the loader's uniqueness guard)
 * inherit these bounds through {@link #walk}.
 */
public final class ReachableStepGraph {

  /**
   * Fail-closed bound on combined reference-following depth (workflow-ref, blueprint-ref, branch and
   * inline-workflow nesting) — far beyond any authorable bundle, low enough to fail before the Java
   * stack does.
   */
  static final int MAX_REF_DEPTH = 500;

  /**
   * Fail-closed bound on total traversal work (visited executables plus replayed memoized records) —
   * a backstop for crafted bundles whose cycles defeat memoization.
   */
  static final int MAX_TRAVERSAL_NODES = 100_000;

  private ReachableStepGraph() {
  }

  /**
   * Walks every reachable step occurrence from {@code root}.
   *
   * @param root     the root workflow to walk from
   * @param resolver resolves {@code WORKFLOW}-step refs to their definitions ({@code null} when absent)
   *
   * @return every reachable step occurrence, deduplicated by structural location
   *
   * @throws IllegalStateException when the traversal exceeds the fail-closed reference-depth or
   *                               traversal-size bound (a pathological bundle)
   */
  public static List<ReachableStep> walk(WorkflowDefinition root, WorkflowRefResolver resolver) {
    Validate.notNull(root, "root must not be null");
    Validate.notNull(resolver, "resolver must not be null");
    Traversal traversal = new Traversal(resolver, root.id());
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
   * @throws IllegalStateException when {@code stepId} is reachable from more than one structural location,
   *                               or when the traversal exceeds a fail-closed bound (see {@link #walk})
   */
  public static StepDefinition resolveUnique(WorkflowDefinition root, String stepId,
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
    return matches.get(0).step();
  }

  /**
   * Finds every step id reachable from {@code root} at two or more structural locations.
   *
   * @param root     the root workflow to walk from
   * @param resolver resolves {@code WORKFLOW}-step refs to their definitions ({@code null} when absent)
   *
   * @return the ambiguous step ids with their conflicting locations, in encounter order; empty when none
   *
   * @throws IllegalStateException when the traversal exceeds a fail-closed bound (see {@link #walk})
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
   * A step record kept relative to a blueprint reference site's container key so a memoized subtree
   * can be replayed at a different site: {@code relativeBaseLocation} is the pre-occurrence-suffix
   * location minus the reference site's container key.
   */
  private record RelativeStep(String relativeBaseLocation, StepDefinition step) {
  }

  /**
   * A fully explored blueprint subtree, valid for replay at any later reference site within the same
   * {@code enclosing} workflow whose descent chain is disjoint from {@code descendedBlueprintIds}
   * (identity-compared frames; a subtree truncated by the cycle guard is never memoized). Records are
   * keyed by their occurrence-suffixed relative location, preserving encounter order.
   */
  private record MemoizedBlueprintWalk(WorkflowDefinition enclosing,
      Map<String, RelativeStep> relativeSteps, Set<String> descendedBlueprintIds) {

    private MemoizedBlueprintWalk {
      relativeSteps = Collections.unmodifiableMap(new LinkedHashMap<>(relativeSteps));
      descendedBlueprintIds = Set.copyOf(descendedBlueprintIds);
    }
  }

  /**
   * Accumulator for one in-progress blueprint-subtree walk: the step records made under the reference
   * site's container key (kept relative to it, keyed by occurrence-suffixed relative location so
   * replayed duplicates collapse), the blueprint ids descended within the subtree, and whether the
   * walk is complete — i.e. never truncated by the cycle guard, and therefore safe to memoize.
   */
  private static final class BlueprintWalk {

    private final int prefixLength;
    private final Map<String, RelativeStep> relativeSteps = new LinkedHashMap<>();
    private final Set<String> descendedBlueprintIds = new HashSet<>();
    private boolean complete = true;

    private BlueprintWalk(int prefixLength) {
      this.prefixLength = prefixLength;
    }
  }

  /**
   * Mutable traversal state for a single walk: the resolver, the global workflow-id visited set, the
   * by-location accumulator, the per-walk blueprint-subtree memo (keyed by definition identity, so
   * duplicate ids can never alias), and the fail-closed depth/size counters. The blueprint-ref path
   * guard is passed per workflow frame.
   */
  private static final class Traversal {

    private final WorkflowRefResolver resolver;
    private final String rootId;
    private final Set<String> visitedWorkflowIds = new HashSet<>();
    private final Map<String, ReachableStep> byLocation = new LinkedHashMap<>();
    private final Map<BlueprintDefinition, MemoizedBlueprintWalk> memoizedBlueprintWalks =
        new IdentityHashMap<>();
    private int refDepth;
    private int visitedNodes;

    private Traversal(WorkflowRefResolver resolver, String rootId) {
      this.resolver = resolver;
      this.rootId = rootId;
    }

    private void enterWorkflow(WorkflowDefinition workflow, String containerKey,
        Set<String> blueprintPath) {
      if (!visitedWorkflowIds.add(workflow.id())) {
        return;
      }
      collect(workflow.steps(), workflow, containerKey, blueprintPath, null);
    }

    /**
     * Collects the reachable steps of one executable list. {@code blueprintWalk} is the accumulator
     * of the innermost enclosing blueprint-subtree walk, or {@code null} in a workflow frame (where
     * records are absolute and need no replay).
     */
    private void collect(List<Executable> executables, WorkflowDefinition enclosing,
        String containerKey, Set<String> blueprintPath, BlueprintWalk blueprintWalk) {
      for (Executable executable : executables) {
        countVisitedNode();
        if (executable instanceof StepDefinition step) {
          recordStep(step, containerKey, blueprintWalk);
          if (step.behaviour() instanceof WorkflowBehaviour workflowBehaviour) {
            WorkflowDefinition sub = resolver.resolve(workflowBehaviour.workflowRef());
            if (sub != null) {
              enterRef();
              enterWorkflow(sub, "wf:" + sub.id(), new HashSet<>());
              exitRef();
            }
          } else if (step.behaviour() instanceof BranchBehaviour branchBehaviour) {
            // A branch's routable children (exact-match targets, predicate targets, the default
            // branch) are reachable at runtime exactly like any sibling step in this same
            // container — descend them here, or they (and any gate on them) are invisible to
            // resume/gate resolution and the duplicate-id guard.
            enterRef();
            collect(branchBehaviour.childExecutables(), enclosing, containerKey, blueprintPath,
                blueprintWalk);
            exitRef();
          }
        } else if (executable instanceof BlueprintRef ref) {
          descendBlueprintRef(ref, enclosing, containerKey, blueprintPath, blueprintWalk);
        } else if (executable instanceof WorkflowDefinition nested) {
          enterRef();
          collect(nested.steps(), nested, "wf:" + nested.id(), new HashSet<>(), null);
          exitRef();
        }
      }
    }

    private void descendBlueprintRef(BlueprintRef ref, WorkflowDefinition enclosing,
        String containerKey, Set<String> blueprintPath, BlueprintWalk enclosingWalk) {
      BlueprintDefinition blueprint = enclosing.blueprints().get(ref.blueprintId());
      if (blueprint == null) {
        return;
      }
      if (blueprintPath.contains(ref.blueprintId())) {
        // Skip a blueprint already on the current descent chain (a blueprint-ref cycle); for an
        // acyclic graph the id is never already present, so this never alters a valid walk. The
        // truncation makes every in-progress subtree above it context-dependent, so poison it —
        // a truncated subtree must never be replayed at another reference site.
        if (enclosingWalk != null) {
          enclosingWalk.complete = false;
        }
        return;
      }
      MemoizedBlueprintWalk memoized = memoizedBlueprintWalks.get(blueprint);
      if (memoized != null && memoized.enclosing() == enclosing
          && Collections.disjoint(memoized.descendedBlueprintIds(), blueprintPath)) {
        replayMemoizedWalk(memoized, containerKey, enclosingWalk);
        return;
      }
      BlueprintWalk walk = new BlueprintWalk(containerKey.length());
      walk.descendedBlueprintIds.add(ref.blueprintId());
      blueprintPath.add(ref.blueprintId());
      enterRef();
      collect(blueprint.steps(), enclosing, containerKey + "/bp:" + ref.blueprintId(),
          blueprintPath, walk);
      exitRef();
      blueprintPath.remove(ref.blueprintId());
      if (walk.complete) {
        memoizedBlueprintWalks.put(blueprint,
            new MemoizedBlueprintWalk(enclosing, walk.relativeSteps, walk.descendedBlueprintIds));
      }
      if (enclosingWalk != null) {
        composeInto(enclosingWalk, walk.complete, walk.relativeSteps, walk.descendedBlueprintIds,
            containerKey);
      }
    }

    /**
     * Replays a memoized blueprint subtree at {@code containerKey}: every cached record is re-issued
     * through the same identity-aware occurrence logic as a fresh walk (in original encounter order,
     * so occurrence suffixes assign identically), then composed into the enclosing subtree walk.
     */
    private void replayMemoizedWalk(MemoizedBlueprintWalk memoized, String containerKey,
        BlueprintWalk enclosingWalk) {
      for (RelativeStep relative : memoized.relativeSteps().values()) {
        countVisitedNode();
        recordOccurrence(relative.step(), containerKey + relative.relativeBaseLocation());
      }
      if (enclosingWalk != null) {
        composeInto(enclosingWalk, true, memoized.relativeSteps(),
            memoized.descendedBlueprintIds(), containerKey);
      }
    }

    /**
     * Folds a nested blueprint-subtree result (fresh or replayed, rooted at {@code nestedRefSiteKey})
     * into the enclosing in-progress walk, re-keying the nested records relative to the enclosing
     * walk's reference site.
     */
    private void composeInto(BlueprintWalk enclosingWalk, boolean nestedComplete,
        Map<String, RelativeStep> nestedRelativeSteps, Set<String> nestedDescendedBlueprintIds,
        String nestedRefSiteKey) {
      enclosingWalk.complete = enclosingWalk.complete && nestedComplete;
      enclosingWalk.descendedBlueprintIds.addAll(nestedDescendedBlueprintIds);
      if (!enclosingWalk.complete) {
        return;
      }
      String relativePrefix = nestedRefSiteKey.substring(enclosingWalk.prefixLength);
      for (Map.Entry<String, RelativeStep> entry : nestedRelativeSteps.entrySet()) {
        RelativeStep nested = entry.getValue();
        enclosingWalk.relativeSteps.putIfAbsent(relativePrefix + entry.getKey(),
            new RelativeStep(relativePrefix + nested.relativeBaseLocation(), nested.step()));
      }
    }

    private void recordStep(StepDefinition step, String containerKey, BlueprintWalk blueprintWalk) {
      String baseLocation = containerKey + "/step:" + step.stepId();
      String recordedLocation = recordOccurrence(step, baseLocation);
      if (blueprintWalk != null) {
        blueprintWalk.relativeSteps.putIfAbsent(
            recordedLocation.substring(blueprintWalk.prefixLength),
            new RelativeStep(baseLocation.substring(blueprintWalk.prefixLength), step));
      }
    }

    /**
     * Records one step occurrence at {@code baseLocation}, returning the (possibly
     * occurrence-suffixed) location the record for this definition lives under.
     */
    private String recordOccurrence(StepDefinition step, String baseLocation) {
      ReachableStep existing = byLocation.get(baseLocation);
      if (existing == null) {
        byLocation.put(baseLocation, new ReachableStep(step.stepId(), baseLocation, step));
        return baseLocation;
      }
      if (existing.step() == step) {
        // The same definition reached via another path to the same container — one location.
        return baseLocation;
      }
      // A different definition occupying the same container path (e.g. two branch arms each
      // defining a step with this id). The definitions may disagree — including on gating
      // transitions — so keep every occurrence distinct: ambiguity detection and resolveUnique
      // must fail closed instead of silently resolving to whichever definition was walked first.
      int occurrence = 2;
      String distinct = baseLocation + "#" + occurrence;
      while (byLocation.containsKey(distinct)) {
        if (byLocation.get(distinct).step() == step) {
          return distinct;
        }
        occurrence++;
        distinct = baseLocation + "#" + occurrence;
      }
      byLocation.put(distinct, new ReachableStep(step.stepId(), distinct, step));
      return distinct;
    }

    private void enterRef() {
      refDepth++;
      Validate.isTrue(refDepth <= MAX_REF_DEPTH, () -> new IllegalStateException(
          ("Reachable step graph of workflow '%s' exceeds the maximum reference depth of %d "
              + "(combined workflow-ref/blueprint-ref/branch/inline-workflow nesting); refusing to "
              + "walk a pathologically deep bundle").formatted(rootId, MAX_REF_DEPTH)));
    }

    private void exitRef() {
      refDepth--;
    }

    private void countVisitedNode() {
      visitedNodes++;
      Validate.isTrue(visitedNodes <= MAX_TRAVERSAL_NODES, () -> new IllegalStateException(
          ("Reachable step graph of workflow '%s' exceeds the maximum traversal size of %d nodes; "
              + "refusing to walk a pathologically large or cyclic fan-out bundle")
              .formatted(rootId, MAX_TRAVERSAL_NODES)));
    }
  }
}
