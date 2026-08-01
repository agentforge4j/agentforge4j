// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;
import java.util.Set;

/**
 * Persists an agent step's {@code COMPLETE} signal onto {@link WorkflowState}, scoped to
 * every loop blueprint whose iteration body is currently active on the call stack (see
 * {@link ExecutionContext#activeLoopBlueprintIds()}).
 *
 * <p>An {@code AGENT_SIGNAL} loop's termination check reads
 * {@link ExecutionContext#isAgentCompletionSignalled()} — a transient, per-drive flag reset at the
 * top of every iteration and never re-set on a resume, since a step that already produced an output
 * is correctly skip-guarded rather than re-invoked. Without a durable counterpart, a {@code COMPLETE}
 * signalled before a <em>later</em> step in the same iteration pauses (for example a body of
 * {@code [AGENT(emits COMPLETE), INPUT]}, or a signalling step gated by {@code HUMAN_REVIEW}) is lost
 * across the pause: the loop re-runs (or re-gates) iterations until {@code maxIterations} instead of
 * terminating cleanly once the human resumes.
 *
 * <p>Shared by {@code AgentBehaviourHandler} and {@code SparBehaviourHandler} — both apply commands
 * that may include a {@code COMPLETE} — so the persistence rule lives in one place rather than two
 * independently maintained copies.
 */
public final class AgentSignalPersistence {

  private AgentSignalPersistence() {
  }

  /**
   * Records or clears {@code signalled} for every loop blueprint currently on the active-loop call
   * stack, overwriting any previous entry so a later, non-signalling agent step in the same iteration
   * correctly un-signals an earlier step's completion — preserving "the body's final decision step
   * controls termination" whether or not a pause intervenes between them. A no-op when no loop
   * iteration is currently active.
   *
   * @param step             the step whose command application just ran; must not be {@code null}
   *                         and must already have a recorded execution uid
   * @param executionContext the current execution context; must not be {@code null}
   * @param signalled        whether the step's command batch applied a {@code COMPLETE}
   */
  public static void record(StepDefinition step, ExecutionContext executionContext,
      boolean signalled) {
    Validate.notNull(step, "step must not be null");
    Validate.notNull(executionContext, "executionContext must not be null");
    Set<String> activeLoopBlueprintIds = executionContext.activeLoopBlueprintIds();
    if (activeLoopBlueprintIds.isEmpty()) {
      return;
    }
    int uid = Validate.notNull(
        executionContext.getState().getStepExecutionUid().get(step.stepId()),
        "stepExecutionUid must be recorded before applying agent commands");
    for (String blueprintId : activeLoopBlueprintIds) {
      if (signalled) {
        executionContext.getState().setAgentSignalCompleted(blueprintId, uid);
      } else {
        executionContext.getState().clearAgentSignalCompleted(blueprintId);
      }
    }
  }
}
