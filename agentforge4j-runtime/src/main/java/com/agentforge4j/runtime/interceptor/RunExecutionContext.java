package com.agentforge4j.runtime.interceptor;

import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.util.Validate;

/**
 * Neutral context for {@link RunExecutionInterceptor#beforeMainExecution(RunExecutionContext)}, supplied once when a
 * run first enters main execution. Carries no tenant or cost concepts — an embedding application derives those from the
 * run id / state.
 *
 * @param runId the run about to enter main execution; never blank
 * @param state a defensive snapshot of the workflow state at main-execution entry (mutating it does not affect the live
 *              run); never {@code null}
 */
public record RunExecutionContext(String runId, WorkflowState state) {

  public RunExecutionContext {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notNull(state, "state must not be null");
  }
}
