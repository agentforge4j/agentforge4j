package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a flat list of {@link Executable}s by delegating to {@link ExecutableExecutor} until the list is exhausted or
 * a non-{@code COMPLETED} outcome is produced.
 *
 * <p>Re-entry safety: when a run is resumed (for example after
 * {@code submitInput}) the whole sequence may be re-driven from the start. Plain {@link StepDefinition} entries whose
 * id is already present in {@code state.stepOutputs} are skipped so we never re-invoke an LLM or re-ask for user input
 * for a step that already produced a result.
 *
 * <p>{@code BlueprintRef} and nested {@code WorkflowDefinition} entries are
 * always re-entered — their own executors are responsible for resume-safety within their bodies.
 */
public final class StepSequenceExecutor {

  private static final System.Logger LOG = System.getLogger(StepSequenceExecutor.class.getName());

  private final ExecutableExecutor executableExecutor;

  public StepSequenceExecutor(ExecutableExecutor executableExecutor) {
    this.executableExecutor = Validate.notNull(executableExecutor,
        "executableExecutor must not be null");
  }

  /**
   * Execute each element in sequence.
   *
   * @return the outcome of the last executable if it was non-{@code COMPLETED}; otherwise {@code COMPLETED} when all
   * executables finished normally.
   */
  public ExecutionOutcome executeAll(List<Executable> executables,
      ExecutionContext executionContext) {
    validate(executables, executionContext);
    ExecuteHelper executeHelper = createExecuteHelper(executionContext);
    for (Executable executable : executables) {
      addStepDefinition(executable, executeHelper);
    }
    executionContext.setCurrentSequenceStepIds(executeHelper.orderedStepIds());
    executionContext.setCurrentSequenceExecutables(executeHelper.executableById());
    LOG.log(System.Logger.Level.DEBUG, "Executing step sequence count={0}, stepIds={1}",
        executeHelper.orderedStepIds().size(), executeHelper.orderedStepIds());

    for (Executable executable : executables) {
      if (executionContext.getState().getStatus() == WorkflowStatus.CANCELLED) {
        return ExecutionOutcome.PAUSED;
      }
      logStepDefinitionResumeCheck(executable, executeHelper);
      if (shouldSkip(executable, executeHelper.stepOutputs())) {
        logSkipping(executable);
        continue;
      }
      ExecutionOutcome outcome = execute(executionContext, executable);
      if (outcome != ExecutionOutcome.COMPLETED) {
        return outcome;
      }
    }
    return ExecutionOutcome.COMPLETED;
  }

  private static void logSkipping(Executable executable) {
    if (executable instanceof StepDefinition stepDef) {
      LOG.log(System.Logger.Level.DEBUG, "Skipping already completed step stepId={0}",
          stepDef.stepId());
    }
  }

  private static void logStepDefinitionResumeCheck(Executable executable,
      ExecuteHelper executeHelper) {
    if (executable instanceof StepDefinition stepDef) {
      LOG.log(System.Logger.Level.DEBUG,
          "Resume check stepId={0}, stepOutputs={1}, willSkip={2}",
          stepDef.stepId(),
          executeHelper.stepOutputs().keySet(),
          executeHelper.stepOutputs().containsKey(stepDef.stepId()));
    }
  }

  private ExecutionOutcome execute(ExecutionContext executionContext,
      Executable executable) {
    if (executable instanceof StepDefinition stepDef) {
      int uid = executionContext.allocateStepSequenceUid();
      executionContext.getState().putStepExecutionUid(stepDef.stepId(), uid);
      LOG.log(System.Logger.Level.DEBUG, "Executing step stepId={0}, behaviourType={1}, uid={2}",
          stepDef.stepId(), stepDef.behaviour().getClass().getSimpleName(), uid);
    }
    ExecutionOutcome outcome = executableExecutor.execute(executable, executionContext);
    if (executable instanceof StepDefinition stepDef && outcome == ExecutionOutcome.COMPLETED) {
      LOG.log(System.Logger.Level.DEBUG, "Step completed stepId={0}, uid={1}",
          stepDef.stepId(),
          executionContext.getState().getStepExecutionUid().get(stepDef.stepId()));
    }
    return outcome;
  }

  private static void addStepDefinition(Executable executable, ExecuteHelper executeHelper) {
    if (executable instanceof StepDefinition stepDef) {
      executeHelper.orderedStepIds().add(stepDef.stepId());
      executeHelper.executableById().put(stepDef.stepId(), executable);
    }
  }

  private static ExecuteHelper createExecuteHelper(ExecutionContext executionContext) {
    return new ExecuteHelper(executionContext.getState().getStepOutputs(),
        new ArrayList<>(),
        new LinkedHashMap<>());
  }

  private record ExecuteHelper(Map<String, String> stepOutputs, List<String> orderedStepIds,
                               Map<String, Executable> executableById) {

  }

  private static void validate(List<Executable> executables, ExecutionContext executionContext) {
    Validate.notNull(executables, "executables must not be null");
    Validate.notNull(executionContext, "executionContext must not be null");
  }

  private static boolean shouldSkip(Executable executable, Map<String, String> stepOutputs) {
    if (executable instanceof StepDefinition step) {
      return stepOutputs.containsKey(step.stepId());
    }
    // A blueprint whose post-loop gate already fired is skipped on resume: its body completed before
    // the gate suspended, so re-entering would re-run (and re-gate) it.
    if (executable instanceof BlueprintRef ref) {
      return stepOutputs.containsKey(TransitionGate.blueprintGateMarker(ref.blueprintId()));
    }
    return false;
  }
}
