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
import java.util.Set;
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
   * Monotonic counter for step execution UIDs. Seeded from the highest uid already persisted on the run's state, so
   * uids allocated in a resume drive continue the run's ordering instead of restarting at 1 — the rewind range logic
   * ({@code WorkflowState.clearEntriesFromUid}) depends on uids growing monotonically across drives. Not shared across
   * executor instances or runs.
   */
  private int stepSequenceUidCounter;
  /**
   * Ordered step ids for the current sequence — set by StepSequenceExecutor for retry support.
   */
  private List<String> currentSequenceStepIds = List.of();
  private Map<String, Executable> currentSequenceExecutables = Map.of();
  /**
   * Full ordered list of every {@link Executable} in the current sequence — unlike
   * {@link #currentSequenceStepIds}/{@link #currentSequenceExecutables} (which only ever hold
   * {@link com.agentforge4j.core.workflow.step.StepDefinition} entries), this also carries
   * {@code BlueprintRef} and nested {@code WorkflowDefinition} entries in their original position.
   * Set by {@code StepSequenceExecutor} alongside the other two so a {@code RetryPreviousBehaviour}
   * can detect a composite executable sitting inside a replay range that only plain step ids would
   * make invisible.
   */
  private List<Executable> currentSequenceExecutableList = List.of();

  /**
   * Stack of blueprint ids of loops whose iteration body is currently executing (innermost at the head) —
   * bracketed by {@link #pushActiveLoopBlueprint(String)}/{@link #popActiveLoopBlueprint()} around each loop
   * strategy's call to run an iteration's body. Lets a rewind issued from <em>inside</em> one of these
   * loops' own currently-active iteration (for example {@code RetryPreviousBehaviourHandler} retrying that
   * iteration's own first-executed step) exclude that loop from {@code WorkflowState.clearEntriesFromUid}'s
   * loop-cursor sweep — the loop is not being externally re-entered, it is still on the call stack and will
   * correctly advance its own bookkeeping when it next starts an iteration.
   */
  private final Deque<String> activeLoopBlueprintIds = new ArrayDeque<>();

  /**
   * Transient, per-drive flag set when an agent applies a {@code COMPLETE} command. An {@code AGENT_SIGNAL} loop reads
   * it after each iteration to detect that the agent signalled clean loop completion. Not persisted: it is always set
   * and read within the same synchronous drive as the {@code COMPLETE}, so a pause/resume starts a fresh context with
   * the flag cleared.
   * -- SETTER -- Records whether the most recent agent command application signalled completion (a
   * command). Set on every agent step so the value reflects the last agent step of an iteration; read by loops to
   * decide whether to terminate.
   *
   */
  @Setter
  private boolean agentCompletionSignalled;

  public ExecutionContext(WorkflowState state, WorkflowDefinition rootWorkflow,
      int maxNestingDepth) {
    this.state = Validate.notNull(state, "state must not be null");
    this.rootWorkflow = Validate.notNull(rootWorkflow, "rootWorkflow must not be null");
    this.maxNestingDepth = Validate.isGreaterThanZero(maxNestingDepth,
        "maxNestingDepth must be greater than zero").intValue();
    this.stepSequenceUidCounter = highestPersistedUid(state);
  }

  private static int highestPersistedUid(WorkflowState state) {
    int max = 0;
    for (Integer uid : state.getStepExecutionUid().values()) {
      if (uid != null && uid > max) {
        max = uid;
      }
    }
    return max;
  }

  /**
   * Returns the next UID assigned to a step when it begins executing in the current sequence pass.
   */
  public int allocateStepSequenceUid() {
    return ++stepSequenceUidCounter;
  }

  /**
   * Returns the uid the next call to {@link #allocateStepSequenceUid()} would assign, without
   * allocating it. Used by loop strategies to record where a loop iteration's body begins.
   */
  public int peekNextStepSequenceUid() {
    return stepSequenceUidCounter + 1;
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

  public void setCurrentSequenceExecutableList(List<Executable> executables) {
    this.currentSequenceExecutableList = Validate.notNull(executables,
        "executables must not be null");
  }

  /**
   * Marks {@code blueprintId}'s loop as currently running an iteration's body. Callers must pair this with
   * {@link #popActiveLoopBlueprint()} (typically in a {@code finally} block) once that body finishes, pauses,
   * or throws.
   */
  public void pushActiveLoopBlueprint(String blueprintId) {
    activeLoopBlueprintIds.push(Validate.notBlank(blueprintId, "blueprintId must not be blank"));
  }

  /**
   * Unmarks the innermost loop pushed via {@link #pushActiveLoopBlueprint(String)}.
   */
  public void popActiveLoopBlueprint() {
    Validate.isTrue(!activeLoopBlueprintIds.isEmpty(),
        "No active loop blueprint to pop for run '" + state.getRunId() + "'");
    activeLoopBlueprintIds.pop();
  }

  /**
   * Returns the blueprint ids of every loop whose iteration body is currently executing (this loop, and any
   * enclosing loop it is nested inside), or an empty set when none is active.
   */
  public Set<String> activeLoopBlueprintIds() {
    return activeLoopBlueprintIds.isEmpty() ? Set.of() : Set.copyOf(activeLoopBlueprintIds);
  }
}
