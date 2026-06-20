// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Locates {@link Executable} nodes (typically {@link StepDefinition}) within a workflow tree.
 */
final class StepTreeSearcher {

  Executable findStep(WorkflowDefinition workflow, String stepId) {
    return Validate.notNull(findInSteps(workflow.steps(), workflow, stepId),
        "Step '%s' not found in workflow '%s'".formatted(stepId, workflow.id()));
  }

  /**
   * Locates a {@link StepDefinition} by id starting at {@code root} and descending into any
   * sub-workflows referenced by {@code WORKFLOW} steps (resolved from {@code repository}), so a step
   * inside a nested sub-workflow frame is found rather than only the root's own tree. Returns
   * {@code null} when no step matches (callers that need a hard failure use {@link #findStep}).
   *
   * <p>Fails closed on ambiguity: if the reachable workflow graph contains the step id at more than
   * one <em>structural location</em> it throws rather than silently returning the first — a silent
   * pick could gate or rewind the wrong step. Matches are deduplicated by their defining-container
   * path, <em>not</em> by {@link StepDefinition} value equality: two different reachable workflows
   * that define an identical step (same id, name and behaviour) occupy two locations and are
   * ambiguous, while a single definition reached by more than one path (e.g. one blueprint
   * referenced twice from the same workflow) shares a location and collapses to one match.
   *
   * @param root       the root workflow to search from
   * @param stepId     the step id to locate
   * @param repository resolves {@code WORKFLOW} step {@code workflowRef}s to their definitions
   *
   * @return the matching step, or {@code null} when none is reachable
   * @throws IllegalStateException if {@code stepId} resolves at more than one structural location
   */
  StepDefinition findStepAcrossWorkflows(WorkflowDefinition root, String stepId,
      WorkflowRepository repository) {
    Map<String, StepDefinition> matchesByLocation = new LinkedHashMap<>();
    collectAcrossWorkflows(root, "wf:" + root.id(), stepId, repository, new HashSet<>(),
        matchesByLocation);
    if (matchesByLocation.isEmpty()) {
      return null;
    }
    int matchCount = matchesByLocation.size();
    Validate.isTrue(matchCount == 1, () -> new IllegalStateException(
        ("Ambiguous step id '%s' resolves to %d structural locations across the reachable workflow "
            + "graph from workflow '%s'; reachable step ids must be unique")
            .formatted(stepId, matchCount, root.id())));
    return matchesByLocation.values().iterator().next();
  }

  private void collectAcrossWorkflows(WorkflowDefinition workflow, String containerKey, String stepId,
      WorkflowRepository repository, Set<String> visited,
      Map<String, StepDefinition> matchesByLocation) {
    if (!visited.add(workflow.id())) {
      return;
    }
    collectFromExecutables(workflow.steps(), workflow, containerKey, stepId, repository, visited,
        matchesByLocation);
  }

  private void collectFromExecutables(List<Executable> executables, WorkflowDefinition enclosing,
      String containerKey, String stepId, WorkflowRepository repository, Set<String> visited,
      Map<String, StepDefinition> matchesByLocation) {
    for (Executable executable : executables) {
      if (executable instanceof StepDefinition step) {
        if (step.stepId().equals(stepId)) {
          // Key by defining-container path so identical steps in two different workflows stay
          // distinct (ambiguous) while one definition reached via two refs to the same blueprint
          // collapses to a single location.
          matchesByLocation.putIfAbsent(containerKey + "/step:" + step.stepId(), step);
        }
        if (step.behaviour() instanceof WorkflowBehaviour workflowBehaviour) {
          WorkflowDefinition sub = repository.findAll().get(workflowBehaviour.workflowRef());
          if (sub != null) {
            collectAcrossWorkflows(sub, "wf:" + sub.id(), stepId, repository, visited,
                matchesByLocation);
          }
        }
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition bp = enclosing.blueprints().get(ref.blueprintId());
        if (bp != null) {
          collectFromExecutables(bp.steps(), enclosing, containerKey + "/bp:" + ref.blueprintId(),
              stepId, repository, visited, matchesByLocation);
        }
      } else if (executable instanceof WorkflowDefinition nested) {
        collectFromExecutables(nested.steps(), nested, "wf:" + nested.id(), stepId, repository,
            visited, matchesByLocation);
      }
    }
  }

  /**
   * Returns the top-level {@link StepDefinition} with the given id, or {@code null} when no direct entry of
   * {@code workflow.steps()} is a step with that id. Does not descend into blueprints or sub-workflows.
   */
  StepDefinition findTopLevelStep(WorkflowDefinition workflow, String stepId) {
    for (Executable executable : workflow.steps()) {
      if (executable instanceof StepDefinition step && step.stepId().equals(stepId)) {
        return step;
      }
    }
    return null;
  }

  /**
   * Returns the id of the top-level entry whose subtree contains {@code stepId} when the step exists only nested inside
   * a blueprint or sub-workflow (the blueprint id or sub-workflow id, respectively), or {@code null} when the step is
   * not found nested under any top-level entry. Used to point a rejected nested-retry request at its enclosing
   * top-level step.
   */
  String findEnclosingTopLevelId(WorkflowDefinition workflow, String stepId) {
    for (Executable executable : workflow.steps()) {
      if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        if (blueprint != null && findInSteps(blueprint.steps(), workflow, stepId) != null) {
          return ref.blueprintId();
        }
      } else if (executable instanceof WorkflowDefinition nested
          && findInSteps(nested.steps(), nested, stepId) != null) {
        return nested.id();
      }
    }
    return null;
  }

  Executable findInSteps(List<Executable> steps, WorkflowDefinition enclosing, String stepId) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step && step.stepId().equals(stepId)) {
        return step;
      } else if (executable instanceof BlueprintRef ref) {
        BlueprintDefinition bp = enclosing.blueprints().get(ref.blueprintId());
        Executable found = bp == null ? null : findInSteps(bp.steps(), enclosing, stepId);
        if (found != null) {
          return found;
        }
      } else if (executable instanceof WorkflowDefinition nested) {
        Executable found = findInSteps(nested.steps(), nested, stepId);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
