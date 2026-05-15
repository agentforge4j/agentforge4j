package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.BranchBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects {@code agentRef} usages from a workflow tree, including blueprint refs, branch targets,
 * and nested workflows, each tagged with the enclosing workflow id for validation diagnostics.
 */
public final class WorkflowAgentRefCollector {

  /**
   * Records a reference to an agent id within a workflow, including the context for validation.
   *
   * @param agentRef            the agent id being referenced
   * @param resolvingWorkflowId the workflow containing the reference
   * @param stepId              the step containing the reference
   */
  public record AgentRefSite(String agentRef, String resolvingWorkflowId, String stepId) {

  }

  private WorkflowAgentRefCollector() {
  }

  /**
   * Collects all agent id references from the workflow tree.
   *
   * @param root the root workflow to traverse
   * @return list of all agent references with context
   */
  public static List<AgentRefSite> collect(WorkflowDefinition root) {
    Validate.notNull(root, "root must not be null");
    List<AgentRefSite> out = new ArrayList<>();
    walkSteps(root.steps(), root, out);
    return List.copyOf(out);
  }

  private static void walkSteps(
      List<Executable> steps,
      WorkflowDefinition scope,
      List<AgentRefSite> out) {
    for (Executable executable : steps) {
      walkExecutable(executable, scope, out);
    }
  }

  private static void walkExecutable(
      Executable executable,
      WorkflowDefinition scope,
      List<AgentRefSite> out) {
    if (executable instanceof StepDefinition step) {
      walkStepBehaviours(step, scope, out);
    } else if (executable instanceof BlueprintRef ref) {
      BlueprintDefinition blueprint = scope.blueprints().get(ref.blueprintId());
      Validate.notNull(blueprint,
          "Workflow '%s' contains BlueprintRef to unknown blueprint '%s'"
              .formatted(scope.id(), ref.blueprintId()));
      walkSteps(blueprint.steps(), scope, out);
    } else if (executable instanceof BlueprintDefinition blueprint) {
      walkSteps(blueprint.steps(), scope, out);
    } else if (executable instanceof WorkflowDefinition nested) {
      walkSteps(nested.steps(), nested, out);
    }
  }

  private static void walkStepBehaviours(
      StepDefinition step,
      WorkflowDefinition scope,
      List<AgentRefSite> out) {
    String workflowId = scope.id();
    StepBehaviour behaviour = step.behaviour();
    if (behaviour instanceof AgentBehaviour ab) {
      out.add(new AgentRefSite(ab.agentRef(), workflowId, step.stepId()));
    } else if (behaviour instanceof SparBehaviour sb) {
      out.add(new AgentRefSite(sb.agentRef(), workflowId, step.stepId()));
      out.add(new AgentRefSite(sb.sparConfig().challengerAgentId(), workflowId, step.stepId()));
    } else if (behaviour instanceof BranchBehaviour bb) {
      bb.branches().values().forEach(branch -> walkExecutable(branch, scope, out));
      if (bb.defaultBranch() != null) {
        walkExecutable(bb.defaultBranch(), scope, out);
      }
    }
  }
}
