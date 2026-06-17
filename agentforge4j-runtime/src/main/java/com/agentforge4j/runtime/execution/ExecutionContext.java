// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Mutable per-run context passed to executors during a drive loop.
 *
 * <p>Holds the live {@link WorkflowState}, the current workflow definition being
 * executed (which changes when a nested workflow is entered), and a stack of workflow ids for cycle detection.
 *
 * <p>Not thread-safe. Each run is driven by a single thread at a time.
 */
@Getter
public final class ExecutionContext {

  /**
   * The mutable state of the current run.
   */
  private final WorkflowState state;

  /**
   * The root workflow of this run — always the one started via {@code start(...)}.
   */
  private final WorkflowDefinition rootWorkflow;

  /**
   * Stack of workflow ids currently on the execution path — used for cycle detection.
   */
  private final Deque<String> workflowStack = new ArrayDeque<>();

  /**
   * Parallel stack of workflow definitions (innermost at the head), used for nested workflow execution and
   * diagnostics.
   */
  private final Deque<WorkflowDefinition> activeWorkflowStack = new ArrayDeque<>();

  /**
   * Maximum nesting depth for nested workflows.
   */
  private final int maxNestingDepth;
  /**
   * Monotonic counter for step execution UIDs within this context (one {@code drive} / outer execution). Not shared
   * across executor instances or runs.
   */
  private int stepSequenceUidCounter;
  /**
   * Ordered step ids for the current sequence — set by StepSequenceExecutor for retry support.
   */
  private List<String> currentSequenceStepIds = List.of();
  private Map<String, Executable> currentSequenceExecutables = Map.of();

  /**
   * Transient, per-drive flag set when an agent applies a {@code COMPLETE} command. An
   * {@code AGENT_SIGNAL} loop reads it after each iteration to detect that the agent signalled clean
   * loop completion. Not persisted: it is always set and read within the same synchronous drive as
   * the {@code COMPLETE}, so a pause/resume starts a fresh context with the flag cleared.
   * -- SETTER --
   *  Records whether the most recent agent command application signalled completion (a
   *
   *  command). Set on every agent step so the value reflects the last agent step of
   *  an iteration; read by
   *  loops to decide whether to terminate.
   *
   * @param signalled {@code true} when a {@code COMPLETE} command was applied

   */
  @Setter
  private boolean agentCompletionSignalled;

  public ExecutionContext(WorkflowState state, WorkflowDefinition rootWorkflow,
      int maxNestingDepth) {
    this.state = Validate.notNull(state, "state must not be null");
    this.rootWorkflow = Validate.notNull(rootWorkflow, "rootWorkflow must not be null");
    this.maxNestingDepth = Validate.isGreaterThanZero(maxNestingDepth,
        "maxNestingDepth must be greater than zero").intValue();
  }

  /**
   * Returns the next UID assigned to a step when it begins executing in the current sequence pass.
   */
  public int allocateStepSequenceUid() {
    return ++stepSequenceUidCounter;
  }

  /**
   * Returns the id of the innermost workflow currently executing: the workflow on top of the active workflow stack when
   * a nested workflow has been entered, or the root workflow id when none has. Always non-null. Used to key invocation
   * identity by the active (possibly nested) workflow, since {@link WorkflowState#getWorkflowId()} is the immutable
   * root for the whole run.
   *
   * @return innermost active workflow id; never {@code null}
   */
  public String getActiveWorkflowId() {
    WorkflowDefinition active = activeWorkflowStack.peek();
    return ObjectUtils.getIfNull(active, rootWorkflow).id();
  }

  public void enterWorkflow(WorkflowDefinition workflow) {
    Validate.notNull(workflow, "workflow must not be null");
    String workflowId = workflow.id();
    Validate.isTrue(!workflowStack.contains(workflowId),
        "Cyclic workflow nesting detected for run '%s': workflow '%s' already on stack %s"
            .formatted(state.getRunId(), workflowId, workflowStack));
    Validate.isTrue(workflowStack.size() < maxNestingDepth,
        "Maximum workflow nesting depth %d exceeded for run '%s'"
            .formatted(maxNestingDepth, state.getRunId()));
    workflowStack.push(workflowId);
    activeWorkflowStack.push(workflow);
  }

  public void exitWorkflow() {
    Validate.isTrue(!workflowStack.isEmpty(), "Cannot exit workflow: stack empty for run '" + state.getRunId() + "'");
    workflowStack.pop();
    activeWorkflowStack.pop();
  }

  public void setCurrentSequenceStepIds(List<String> stepIds) {
    this.currentSequenceStepIds = Validate.notNull(stepIds, "stepIds must not be null");
  }

  public void setCurrentSequenceExecutables(Map<String, Executable> executables) {
    this.currentSequenceExecutables = Validate.notNull(executables, "executables must not be null");
  }
}
