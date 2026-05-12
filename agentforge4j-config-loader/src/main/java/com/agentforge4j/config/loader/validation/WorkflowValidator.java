package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector;
import com.agentforge4j.core.workflow.WorkflowAgentRefCollector.AgentRefSite;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.InputBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.RetryPreviousBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.WorkflowBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkflowValidator {

  /**
   * Verifies that workflow references point to known workflows.
   *
   * @param workflows workflows to validate
   * @throws IllegalArgumentException when a workflow reference targets an unknown workflow
   */
  public void validateWorkflowRefs(Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> ignoredCircularWorkflows = new HashMap<>();
    workflows.values()
        .forEach(workflow -> walkForWorkflowRefs(workflow.steps(), workflow, workflows,
            ignoredCircularWorkflows));
  }

  /**
   * Verifies that blueprint references point to declared blueprints in the same workflow.
   *
   * @param workflows workflows to validate
   * @throws IllegalArgumentException when a blueprint reference targets an unknown blueprint
   */
  public void validateBlueprintRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForBlueprintRefs(workflow.steps(), workflow));
  }

  /**
   * Verifies that artifact references point to declared artifacts in the same workflow.
   *
   * @param workflows workflows to validate
   * @throws IllegalArgumentException when an artifact reference targets an unknown artifact
   */
  public void validateArtifactRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> walkForArtifactRefs(workflow.steps(), workflow));
  }

  /**
   * Verifies that workflow reference graphs do not contain cycles.
   *
   * @param workflows workflows to validate
   * @throws IllegalStateException when a circular workflow reference is detected
   */
  public void validateCircularRefs(Map<String, WorkflowDefinition> workflows) {
    Map<String, List<String>> circularWorkflows = new HashMap<>();
    workflows.values().forEach(workflow ->
        walkForWorkflowRefs(workflow.steps(), workflow, workflows, circularWorkflows));
    validateCircularDependencies(circularWorkflows);
  }

  /**
   * Verifies that {@code RetryPreviousBehaviour} references target known steps.
   *
   * @param workflows workflows to validate
   * @throws IllegalArgumentException when a retry step reference targets an unknown step id
   */
  public void validateRetryStepRefs(Map<String, WorkflowDefinition> workflows) {
    workflows.values().forEach(workflow -> {
      Set<String> workflowStepIds = new HashSet<>();
      collectStepIds(workflow.steps(), workflowStepIds);
      walkForRetryStepRefs(workflow.steps(), workflow, workflowStepIds);
    });
  }

  private void walkForWorkflowRefs(
      List<Executable> steps,
      WorkflowDefinition workflow,
      Map<String, WorkflowDefinition> workflows,
      Map<String, List<String>> circularWorkflows) {
    for (Executable executable : steps) {
      if (executable instanceof StepDefinition step) {
        if (step.behaviour() instanceof WorkflowBehaviour wb) {
          assertWorkflowExists(wb.workflowRef(), step.stepId(), workflow.id(), workflows,
              circularWorkflows);
        }
      } else if (executable instanceof BlueprintRef) {
        // No workflow refs to validate here.
      } else if (executable instanceof BlueprintDefinition blueprint) {
        walkForWorkflowRefs(blueprint.steps(), workflow, workflows, circularWorkflows);
      } else if (executable instanceof WorkflowDefinition nested) {
        walkForWorkflowRefs(nested.steps(), nested, workflows, circularWorkflows);
      }
    }
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

  private void validateCircularDependencies(Map<String, List<String>> circularWorkflows) {
    circularWorkflows.entrySet().stream()
        .filter(entry -> entry.getValue().contains(entry.getKey()))
        .findFirst()
        .ifPresent(entry -> {
              throw new IllegalStateException(
                  "Workflow '%s' has circular references with workflows: %s"
                      .formatted(entry.getKey(), entry.getValue()));
            }
        );
  }

  private static void assertWorkflowExists(String workflowRef, String stepId, String workflowId,
      Map<String, WorkflowDefinition> workflows, Map<String, List<String>> circularWorkflows) {
    Validate.isTrue(workflows.containsKey(workflowRef),
        "Step '%s' in workflow '%s' references unknown workflow '%s'"
            .formatted(stepId, workflowId, workflowRef));
    if (circularWorkflows.containsKey(workflowId)) {
      circularWorkflows.get(workflowId).add(workflowRef);
    } else {
      circularWorkflows.put(workflowId, new ArrayList<>(List.of(workflowRef)));
    }
    List<List<String>> toUpdate = circularWorkflows.values().stream()
        .filter(refs -> refs.contains(workflowId))
        .toList();
    toUpdate.forEach(refs -> refs.add(workflowRef));
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
   * Verifies that every agent reference in every workflow resolves to a known agent id.
   *
   * @param workflows workflows whose agent references are checked
   * @param agents    available agents keyed by id
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
