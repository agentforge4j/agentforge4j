// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.behaviour.AgentBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.CompactBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.LlmSummary;
import com.agentforge4j.core.workflow.step.behaviour.SparBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.util.Validate;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects {@code agentRef} usages from a workflow tree, including blueprint refs, branch targets,
 * and nested workflows, each tagged with the enclosing workflow id for validation diagnostics.
 */
public final class WorkflowAgentRefCollector {

  /**
   * Maximum nesting depth traversed before failing fast. Mirrors {@link WorkflowTreeWalker#MAX_TRAVERSAL_DEPTH}: a
   * circular blueprint reference (a blueprint whose steps reference the blueprint itself) fails with a clear error
   * instead of a {@link StackOverflowError}.
   */
  public static final int MAX_TRAVERSAL_DEPTH = WorkflowTreeWalker.MAX_TRAVERSAL_DEPTH;

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
   *
   * @return list of all agent references with context
   *
   * @throws IllegalArgumentException if the tree nests deeper than {@link #MAX_TRAVERSAL_DEPTH},
   *                                  which indicates a circular blueprint reference
   */
  public static List<AgentRefSite> collect(WorkflowDefinition root) {
    Validate.notNull(root, "root must not be null");
    List<AgentRefSite> out = new ArrayList<>();
    WorkflowTreeWalker.walk(root, (step, scope) -> collectStepRefs(step, scope, out));
    return List.copyOf(out);
  }

  private static void collectStepRefs(StepDefinition step, WorkflowDefinition scope, List<AgentRefSite> out) {
    String workflowId = scope.id();
    StepBehaviour behaviour = step.behaviour();
    if (behaviour instanceof AgentBehaviour ab) {
      out.add(new AgentRefSite(ab.agentRef(), workflowId, step.stepId()));
    } else if (behaviour instanceof SparBehaviour sb) {
      out.add(new AgentRefSite(sb.agentRef(), workflowId, step.stepId()));
      out.add(new AgentRefSite(sb.sparConfig().challengerAgentId(), workflowId, step.stepId()));
    } else if (behaviour instanceof CompactBehaviour cb && cb.mode() instanceof LlmSummary ls) {
      out.add(new AgentRefSite(ls.agentRef(), workflowId, step.stepId()));
    }
  }
}
