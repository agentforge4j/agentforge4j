// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.core.workflow.BlueprintStructureException;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector.AgentRefSite;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowTreeWalker;
import com.agentforge4j.core.workflow.reachability.AmbiguousStepId;
import com.agentforge4j.core.workflow.reachability.ReachableStepGraph;
import com.agentforge4j.core.workflow.reachability.WorkflowRefResolver;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ContextEqualityContract;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.ValidateBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural and cross-reference validation checks run against loaded workflow and agent
 * definitions.
 */
public final class WorkflowValidator {

  private final int maxTraversalDepth;

  /**
   * Creates a validator whose traversal-based checks fail fast past {@code maxTraversalDepth}
   * levels of branch, retry-fallback, blueprint, or inline nested-workflow nesting.
   *
   * @param maxTraversalDepth the maximum nesting depth to traverse before failing fast; must be
   *                          greater than zero. Callers with no reason to diverge from the
   *                          traversal engine's own default should pass
   *                          {@link WorkflowTreeWalker#MAX_TRAVERSAL_DEPTH}.
   */
  public WorkflowValidator(int maxTraversalDepth) {
    this.maxTraversalDepth = Validate.isGreaterThanZero(maxTraversalDepth,
        "maxTraversalDepth must be greater than zero").intValue();
  }

  /**
   * Verifies that workflow references point to known workflows.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a workflow reference targets an unknown workflow
   */
  public void validateWorkflowRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> runIgnoringBlueprintStructure(() ->
        WorkflowTreeWalker.walk(workflow, maxTraversalDepth, (step, scope) -> {
          if (step.behaviour() instanceof WorkflowBehaviour wb) {
            assertWorkflowExists(wb.workflowRef(), step.stepId(), scope.id(), workflows);
          }
        })));
  }

  /**
   * Verifies that blueprint references point to declared blueprints in the same workflow and that
   * no blueprint nesting chain is circular.
   *
   * @param workflows workflows to validate
   *
   * @throws BlueprintStructureException when a blueprint reference targets an unknown blueprint, or
   *                                      nesting exceeds the configured traversal depth
   */
  public void validateBlueprintRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> WorkflowTreeWalker.validateStructure(workflow, maxTraversalDepth));
  }

  /**
   * Verifies that artifact references point to declared artifacts in the same workflow.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when an artifact reference targets an unknown artifact
   */
  public void validateArtifactRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> runIgnoringBlueprintStructure(() ->
        WorkflowTreeWalker.walk(workflow, maxTraversalDepth, (step, scope) -> {
          if (step.behaviour() instanceof InputBehaviour ib) {
            assertArtifactExists(ib.artifactId(), step.stepId(), scope.id(), scope);
          }
        })));
  }

  /**
   * Verifies that workflow reference graphs do not contain cycles.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalStateException when a circular workflow reference is detected
   */
  public void validateCircularRefs(Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> adjacency = buildWorkflowRefAdjacency(workflows);
    detectWorkflowRefCycles(adjacency);
  }

  /**
   * Verifies that {@code RetryPreviousBehaviour} references target a known step reachable within
   * the retry step's own enclosing scope (the root workflow, or the nearest inline nested
   * {@link WorkflowDefinition} — blueprint bodies share their enclosing workflow's scope).
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a retry step reference targets an unknown step id
   */
  public void validateRetryStepRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> {
      Map<WorkflowDefinition, Set<String>> stepIdsByScope = new IdentityHashMap<>();
      List<PendingRetryCheck> pendingChecks = new ArrayList<>();
      try {
        WorkflowTreeWalker.walk(workflow, maxTraversalDepth, (step, scope) -> {
          stepIdsByScope.computeIfAbsent(scope, s -> new HashSet<>()).add(step.stepId());
          if (step.behaviour() instanceof RetryPreviousBehaviour behaviour) {
            pendingChecks.add(new PendingRetryCheck(step.stepId(), scope, behaviour.retryStepId()));
          }
        });
      } catch (BlueprintStructureException ignored) {
        // Reported by validateBlueprintRefs; retry targets already discovered from the rest of the
        // tree are still checked below instead of being discarded along with the walk failure.
      }
      for (PendingRetryCheck check : pendingChecks) {
        Set<String> scopeStepIds = stepIdsByScope.getOrDefault(check.scope(), Set.of());
        Validate.isTrue(scopeStepIds.contains(check.retryStepId()),
            "RetryPreviousBehaviour in step '%s' of workflow '%s' references unknown step '%s'"
                .formatted(check.stepId(), check.scope().id(), check.retryStepId()));
      }
    });
  }

  /**
   * A retry-target check deferred until after a full tree walk completes, since the retry step's
   * enclosing scope may not have collected every one of its own step ids yet at the point the retry
   * step itself is visited.
   */
  private record PendingRetryCheck(String stepId, WorkflowDefinition scope, String retryStepId) {

  }

  /**
   * Runs {@code action}, treating a thrown {@link BlueprintStructureException} as belonging to
   * {@link #validateBlueprintRefs}'s concern rather than this check's own: the underlying blueprint
   * (missing reference, or circular nesting) is reported once, by the one check whose job is to
   * report it, instead of being misattributed to or duplicated by every other check that happens to
   * traverse the same broken branch.
   */
  private static void runIgnoringBlueprintStructure(Runnable action) {
    try {
      action.run();
    } catch (BlueprintStructureException ignored) {
      // Reported by validateBlueprintRefs; not this check's concern.
    }
  }

  /**
   * Verifies that, for every workflow treated as a run root, no step id is reachable from more than one structural
   * location across the reachable graph — the root, the blueprints it references, and the sub-workflows its
   * {@code WORKFLOW} steps reach. This is the exact graph the runtime resolves a step against at a gate or input
   * submission; rejecting the ambiguity here turns an otherwise mid-run {@link IllegalStateException} into a load-time
   * error.
   *
   * <p>Validation is per-root: the same id appearing in two <em>different</em> root workflows is fine
   * (they are separate runs), while a single sub-workflow or blueprint reached by more than one path within one root
   * collapses to a single location and is not ambiguous.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalStateException when a reachable step id resolves to two or more structural locations
   */
  public void validateReachableStepIdUniqueness(Map<String, WorkflowDefinition> workflows) {
    WorkflowRefResolver resolver = workflows::get;
    for (WorkflowDefinition root : workflows.values()) {
      List<AmbiguousStepId> ambiguous = ReachableStepGraph.findAmbiguousStepIds(root, resolver);
      Validate.isTrue(ambiguous.isEmpty(), () -> new IllegalStateException(
          ("Workflow '%s' has step ids reachable from multiple structural locations; reachable step "
              + "ids must be unique: %s").formatted(root.id(), describeAmbiguous(ambiguous))));
    }
  }

  private static String describeAmbiguous(List<AmbiguousStepId> ambiguous) {
    List<String> parts = new ArrayList<>();
    for (AmbiguousStepId entry : ambiguous) {
      parts.add("step id '%s' at %s".formatted(entry.stepId(), entry.locations()));
    }
    return String.join("; ", parts);
  }

  private Map<String, List<String>> buildWorkflowRefAdjacency(
      Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> adjacency = new LinkedHashMap<>();
    for (WorkflowDefinition workflow : workflows.values()) {
      List<String> refs = new ArrayList<>();
      runIgnoringBlueprintStructure(() -> WorkflowTreeWalker.walk(workflow, maxTraversalDepth, (step, scope) -> {
        if (step.behaviour() instanceof WorkflowBehaviour wb) {
          refs.add(wb.workflowRef());
        }
      }));
      adjacency.put(workflow.id(), List.copyOf(refs));
    }
    return Map.copyOf(adjacency);
  }

  private void detectWorkflowRefCycles(Map<String, List<String>> adjacency) {
    Set<String> visited = new HashSet<>();
    for (String workflowId : adjacency.keySet()) {
      if (!visited.contains(workflowId)) {
        dfsForCycle(workflowId, adjacency, visited, new HashSet<>(), new ArrayList<>());
      }
    }
  }

  private void dfsForCycle(
      String workflowId,
      Map<String, List<String>> adjacency,
      Set<String> visited,
      Set<String> inStack,
      List<String> path) {
    if (inStack.contains(workflowId)) {
      int cycleStart = path.indexOf(workflowId);
      List<String> cyclePath = new ArrayList<>(path.subList(cycleStart, path.size()));
      cyclePath.add(workflowId);
      throw new IllegalStateException(
          "Workflow '%s' has circular references: %s"
              .formatted(workflowId, String.join(" -> ", cyclePath)));
    }
    if (visited.contains(workflowId)) {
      return;
    }
    visited.add(workflowId);
    inStack.add(workflowId);
    path.add(workflowId);
    for (String ref : adjacency.getOrDefault(workflowId, List.of())) {
      if (adjacency.containsKey(ref)) {
        dfsForCycle(ref, adjacency, visited, inStack, path);
      }
    }
    path.remove(path.size() - 1);
    inStack.remove(workflowId);
  }

  /**
   * Verifies that every {@code VALIDATE} step's context-equality contracts reference an artifact in that step's own
   * {@code requiredArtifacts} allowlist. A contract pointing at a path outside the allowlist can never be satisfied at
   * runtime (that artifact is never captured for the step), so it is a config error caught here at load time.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a contract references an artifact outside the step's allowlist
   */
  public void validateValidateBehaviourContracts(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> runIgnoringBlueprintStructure(() ->
        WorkflowTreeWalker.walk(workflow, maxTraversalDepth, (step, scope) -> {
          if (step.behaviour() instanceof ValidateBehaviour validate) {
            assertContractsWithinAllowlist(validate, step.stepId(), scope.id());
          }
        })));
  }

  private static void assertContractsWithinAllowlist(ValidateBehaviour validate, String stepId,
      String workflowId) {
    List<String> allowlist = validate.requiredArtifacts();
    for (ContextEqualityContract contract : validate.contextEqualityContracts()) {
      Validate.isTrue(allowlist.contains(contract.artifactPath()),
          ("VALIDATE step '%s' in workflow '%s' has an equality contract on artifact '%s' which is not in its "
              + "requiredArtifacts allowlist %s")
              .formatted(stepId, workflowId, contract.artifactPath(), allowlist));
    }
  }

  private static void assertWorkflowExists(String workflowRef, String stepId, String workflowId,
      Map<String, WorkflowDefinition> workflows) {
    Validate.isTrue(workflows.containsKey(workflowRef),
        "Step '%s' in workflow '%s' references unknown workflow '%s'"
            .formatted(stepId, workflowId, workflowRef));
  }

  private static void assertArtifactExists(String artifactId, String stepId, String workflowId,
      WorkflowDefinition workflow) {
    Validate.isTrue(workflow.artifacts().containsKey(artifactId),
        "Step '%s' in workflow '%s' references unknown artifact '%s'"
            .formatted(stepId, workflowId, artifactId));
  }

  /**
   * Verifies the structural integrity of each workflow's {@code requirements} declarations: requirement-id uniqueness,
   * that a targeted {@code stepId} resolves to a real step, and that no two requirements of the same type target the
   * same site. Requirement {@code type}, {@code action} values, and {@code default} payloads are opaque and are not
   * interpreted here.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a requirement declaration is structurally invalid
   */
  public void validateRequirements(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(this::validateWorkflowRequirements);
  }

  private void validateWorkflowRequirements(WorkflowDefinition workflow) {
    List<WorkflowRequirement> requirements = workflow.requirements();
    if (requirements.isEmpty()) {
      return;
    }
    Set<String> stepIds = new HashSet<>();
    runIgnoringBlueprintStructure(() -> WorkflowTreeWalker.walk(workflow, maxTraversalDepth,
        (step, scope) -> stepIds.add(step.stepId())));
    Set<String> seenIds = new HashSet<>();
    Set<String> seenTargets = new HashSet<>();
    for (WorkflowRequirement requirement : requirements) {
      Validate.isTrue(seenIds.add(requirement.id()),
          "Workflow '%s' declares duplicate requirement id '%s'"
              .formatted(workflow.id(), requirement.id()));
      if (requirement.stepId() != null) {
        Validate.isTrue(stepIds.contains(requirement.stepId()),
            "Requirement '%s' in workflow '%s' targets unknown step '%s'"
                .formatted(requirement.id(), workflow.id(), requirement.stepId()));
      }
      String target = "%s|%s|%s|%s".formatted(requirement.type(), requirement.scope(),
          requirement.stepId(), requirement.action());
      Validate.isTrue(seenTargets.add(target),
          "Workflow '%s' declares conflicting requirements of type '%s' for the same target"
              .formatted(workflow.id(), requirement.type()));
    }
  }

  /**
   * Verifies that no step in any workflow uses {@link CollectionBehaviour}. The {@code COLLECTION}
   * step type is a half-landed public surface: its sealed permit, state, and event types are kept
   * intact for a planned future completion, but no runtime {@code BehaviourHandler} is registered for
   * it, so a run reaching such a step can only fail deep inside execution. The JSON schema's
   * step-type enum already omits {@code COLLECTION}, so this check only ever fires against a
   * programmatically-constructed {@link WorkflowDefinition} tree; it exists as defense-in-depth
   * alongside that schema gate so a future schema change cannot silently reopen the load path.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a step uses {@code CollectionBehaviour}
   */
  public void validateNoCollectionSteps(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> runIgnoringBlueprintStructure(() ->
        WorkflowTreeWalker.walk(workflow, maxTraversalDepth, (step, scope) -> {
          if (step.behaviour() instanceof CollectionBehaviour) {
            throw new IllegalArgumentException(
                ("Step '%s' in workflow '%s' uses CollectionBehaviour (COLLECTION), which has no "
                    + "registered runtime handler in this release and is rejected at load time")
                    .formatted(step.stepId(), scope.id()));
          }
        })));
  }

  /**
   * Verifies that every agent reference in every workflow resolves to a known agent id.
   *
   * @param workflows workflows whose agent references are checked
   * @param agents    available agents keyed by id
   *
   * @throws UnresolvedAgentReferenceException when one or more agent references are unresolved
   */
  public void validateAgentRefs(
      Map<String, WorkflowDefinition> workflows,
      Map<String, AgentDefinition> agents) {
    List<String> missing = new ArrayList<>();
    for (WorkflowDefinition workflow : workflows.values()) {
      for (AgentRefSite site : WorkflowAgentRefCollector.collectIgnoringBlueprintStructureDefects(workflow)) {
        if (!agents.containsKey(site.agentRef())) {
          missing.add("workflow '%s' step '%s' references unknown agent '%s'"
              .formatted(site.resolvingWorkflowId(), site.stepId(), site.agentRef()));
        }
      }
    }
    Validate.isTrue(missing.isEmpty(), () -> new UnresolvedAgentReferenceException(missing));
  }
}
