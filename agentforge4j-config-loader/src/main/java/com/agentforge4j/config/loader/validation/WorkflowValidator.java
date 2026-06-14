package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector.AgentRefSite;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
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

public final class WorkflowValidator {

  /**
   * Verifies that workflow references point to known workflows.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a workflow reference targets an unknown workflow
   */
  public void validateWorkflowRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values()
        .forEach(workflow -> walkForWorkflowRefExistence(workflow.steps(), workflow, workflows));
  }

  /**
   * Verifies that blueprint references point to declared blueprints in the same workflow.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a blueprint reference targets an unknown blueprint
   */
  public void validateBlueprintRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForBlueprintRefs(workflow.steps(), workflow));
  }

  /**
   * Verifies that artifact references point to declared artifacts in the same workflow.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when an artifact reference targets an unknown artifact
   */
  public void validateArtifactRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForArtifactRefs(workflow.steps(), workflow));
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
   * Verifies that {@code RetryPreviousBehaviour} references target known steps.
   *
   * @param workflows workflows to validate
   *
   * @throws IllegalArgumentException when a retry step reference targets an unknown step id
   */
  public void validateRetryStepRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> {
      Set<String> workflowStepIds = new HashSet<>();
      collectStepIds(workflow.steps(), workflowStepIds);
      walkForRetryStepRefs(workflow.steps(), workflow, workflowStepIds);
    });
  }

  private void walkForWorkflowRefExistence(
      List<Executable> steps,
      WorkflowDefinition workflow,
      Map<String, WorkflowDefinition> workflows) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof WorkflowBehaviour wb) {
          assertWorkflowExists(wb.workflowRef(), step.stepId(), workflow.id(), workflows);
        }
      } else if (executable instanceof BlueprintRef) {
        // No workflow refs to validate here.
      } else if (executable instanceof BlueprintDefinition blueprint) {
        walkForWorkflowRefExistence(blueprint.steps(), workflow, workflows);
      } else if (executable instanceof WorkflowDefinition nested) {
        walkForWorkflowRefExistence(nested.steps(), nested, workflows);
      }
    }
  }

  private Map<String, List<String>> buildWorkflowRefAdjacency(
      Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> adjacency = new LinkedHashMap<>();
    for (WorkflowDefinition workflow : workflows.values()) {
      List<String> refs = new ArrayList<>();
      collectWorkflowRefs(workflow.steps(), refs);
      adjacency.put(workflow.id(), List.copyOf(refs));
    }
    return Map.copyOf(adjacency);
  }

  private void collectWorkflowRefs(List<Executable> steps, List<String> refs) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof WorkflowBehaviour wb) {
          refs.add(wb.workflowRef());
        }
      } else if (executable instanceof BlueprintRef) {
        // No workflow refs to collect here.
      } else if (executable instanceof BlueprintDefinition blueprint) {
        collectWorkflowRefs(blueprint.steps(), refs);
      } else if (executable instanceof WorkflowDefinition nested) {
        collectWorkflowRefs(nested.steps(), refs);
      }
    }
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

  private void walkForBlueprintRefs(List<Executable> steps, WorkflowDefinition workflow) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof BranchBehaviour branchBehaviour) {
          walkForBlueprintRefs(branchBehaviour.branches().values().stream().toList(), workflow);
          if (branchBehaviour.defaultBranch() != null) {
            walkForBlueprintRefs(List.of(branchBehaviour.defaultBranch()), workflow);
          }
        }
      } else if (executable instanceof BlueprintRef ref) {
        validateBlueprintExists(ref.blueprintId(), workflow);
        BlueprintDefinition blueprint = workflow.blueprints().get(ref.blueprintId());
        Validate.notNull(blueprint,
            "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
                .formatted(workflow.id(), ref.blueprintId()));
        walkForBlueprintRefs(blueprint.steps(), workflow);
      } else if (executable instanceof BlueprintDefinition blueprint) {
        walkForBlueprintRefs(blueprint.steps(), workflow);
      } else if (executable instanceof WorkflowDefinition nested) {
        walkForBlueprintRefs(nested.steps(), nested);
      }
    }
  }

  private void walkForArtifactRefs(List<Executable> steps, WorkflowDefinition workflow) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof InputBehaviour ib) {
          assertArtifactExists(ib.artifactId(), step.stepId(), workflow.id(), workflow);
        }
      } else if (executable instanceof BlueprintRef) {
        // No artifact refs to validate here.
      } else if (executable instanceof BlueprintDefinition blueprint) {
        walkForArtifactRefs(blueprint.steps(), workflow);
      } else if (executable instanceof WorkflowDefinition nested) {
        walkForArtifactRefs(nested.steps(), nested);
      }
    }
  }

  private static void assertWorkflowExists(String workflowRef, String stepId, String workflowId,
      Map<String, WorkflowDefinition> workflows) {
    Validate.isTrue(workflows.containsKey(workflowRef),
        "Step '%s' in workflow '%s' references unknown workflow '%s'"
            .formatted(stepId, workflowId, workflowRef));
  }

  private static void validateBlueprintExists(String blueprintId, WorkflowDefinition workflow) {
    Validate.isTrue(workflow.blueprints().containsKey(blueprintId),
        "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
            .formatted(workflow.id(), blueprintId));
  }

  private static void assertArtifactExists(String artifactId, String stepId, String workflowId,
      WorkflowDefinition workflow) {
    Validate.isTrue(workflow.artifacts().containsKey(artifactId),
        "Step '%s' in workflow '%s' references unknown artifact '%s'"
            .formatted(stepId, workflowId, artifactId));
  }

  private static void collectStepIds(List<Executable> steps, Set<String> stepIds) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        stepIds.add(step.stepId());
      } else if (executable instanceof BlueprintRef) {
        // No step ids to collect here.
      } else if (executable instanceof BlueprintDefinition blueprint) {
        collectStepIds(blueprint.steps(), stepIds);
      } else if (executable instanceof WorkflowDefinition nested) {
        collectStepIds(nested.steps(), stepIds);
      }
    }
  }

  private static void walkForRetryStepRefs(List<Executable> steps,
      WorkflowDefinition workflow,
      Set<String> workflowStepIds) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof RetryPreviousBehaviour behaviour) {
          Validate.isTrue(workflowStepIds.contains(behaviour.retryStepId()),
              "RetryPreviousBehaviour in step '%s' of workflow '%s' references unknown step '%s'"
                  .formatted(step.stepId(), workflow.id(), behaviour.retryStepId()));
        }
      } else if (executable instanceof BlueprintRef) {
        // No retry refs to validate here.
      } else if (executable instanceof BlueprintDefinition blueprint) {
        walkForRetryStepRefs(blueprint.steps(), workflow, workflowStepIds);
      } else if (executable instanceof WorkflowDefinition nested) {
        Set<String> nestedStepIds = new HashSet<>();
        collectStepIds(nested.steps(), nestedStepIds);
        walkForRetryStepRefs(nested.steps(), nested, nestedStepIds);
      }
    }
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
    workflows.values().forEach(WorkflowValidator::validateWorkflowRequirements);
  }

  private static void validateWorkflowRequirements(WorkflowDefinition workflow) {
    List<WorkflowRequirement> requirements = workflow.requirements();
    if (requirements.isEmpty()) {
      return;
    }
    Set<String> stepIds = new HashSet<>();
    collectStepIds(workflow.steps(), stepIds);
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
      for (AgentRefSite site : WorkflowAgentRefCollector.collect(workflow)) {
        if (!agents.containsKey(site.agentRef())) {
          missing.add("workflow '%s' step '%s' references unknown agent '%s'"
              .formatted(site.resolvingWorkflowId(), site.stepId(), site.agentRef()));
        }
      }
    }
    Validate.isTrue(missing.isEmpty(), () -> new UnresolvedAgentReferenceException(missing));
  }
}
