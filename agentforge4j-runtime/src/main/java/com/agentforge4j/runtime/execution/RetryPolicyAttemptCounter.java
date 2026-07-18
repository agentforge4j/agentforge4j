// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.state.WorkflowState;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Reserved, runtime-owned counter of retry attempts made against a target step's own
 * {@link com.agentforge4j.core.workflow.step.retry.RetryPolicy}, keyed by that target step's id and
 * shared by every mechanism that can retry it — {@code WorkflowRuntime.retry()} and a
 * {@code RETRY_PREVIOUS} step targeting it alike. {@code RetryPolicy.maxAttempts} is the hard
 * aggregate ceiling for retries of that step across both mechanisms; an attempt made through one
 * counts against the budget the other observes.
 *
 * <p>Written only through this class, via {@link WorkflowState#putContextValue}, never through
 * {@code SetContextCommandHandler} (which rejects every {@code __}-prefixed key unconditionally),
 * so an LLM-emitted command can never reset or inflate it. Being {@code __}-prefixed, it is also
 * exempt from {@link WorkflowState#clearEntriesFromUid}'s uid-based sweep, and survives a
 * persist/reload cycle like any other context value.
 */
public final class RetryPolicyAttemptCounter {

  private static final String KEY_PREFIX = "__retry_policy_";
  private static final String KEY_SUFFIX = "_attempts";

  private RetryPolicyAttemptCounter() {
  }

  /**
   * Reads the number of attempts already made against {@code targetStepId}'s {@code RetryPolicy}
   * budget, across every mechanism sharing this counter.
   *
   * @param state        the run state
   * @param targetStepId the retried step's id
   *
   * @return the attempt count, or {@code 0} if none has been recorded yet
   */
  public static int read(WorkflowState state, String targetStepId) {
    ContextValue existing = state.getContext().get(key(targetStepId));
    if (existing instanceof StringContextValue stringContextValue) {
      return NumberUtils.toInt(stringContextValue.value(), 0);
    }
    return 0;
  }

  /**
   * Records one more attempt against {@code targetStepId}'s {@code RetryPolicy} budget. Callers
   * must only invoke this once a retry has been fully accepted for actual execution — every
   * rejecting precondition must be validated first, and a retry that is not genuinely attempted
   * (for example a {@code RETRY_PREVIOUS} fallback, which never re-executes the target) must never
   * call this.
   *
   * <p>Prefer {@link #reserve} over calling this directly after a separate {@link #read}: a
   * check-then-call-this sequence is not atomic against a concurrent caller doing the same for the
   * same {@code targetStepId} — see {@link #reserve}.
   *
   * @param state        the run state
   * @param targetStepId the retried step's id
   */
  public static void increment(WorkflowState state, String targetStepId) {
    int next = read(state, targetStepId) + 1;
    state.putContextValue(key(targetStepId),
        new StringContextValue(String.valueOf(next), ContextProvenance.SYSTEM_GENERATED));
  }

  /**
   * Atomically checks {@code targetStepId}'s shared attempt count against {@code maxAttempts} and,
   * only if still under the ceiling, records one more attempt — a single indivisible
   * check-and-increment, not a separate {@link #read} followed by a separate {@link #increment}.
   *
   * <p>{@code WorkflowRuntime.retry()} and a {@code RETRY_PREVIOUS} step targeting the same step
   * share this exact counter and can race each other for the same run's {@link WorkflowState} — the
   * same in-process, live-mutable instance both mechanisms observe (see
   * {@code DefaultWorkflowRuntime}'s run-lock-stripe javadoc for the general shape of this hazard). A
   * separate read-then-increment lets two concurrent callers both observe the count just under the
   * ceiling and both proceed, together exceeding {@code maxAttempts}; synchronizing the read and the
   * increment on {@code state} here — the one monitor every caller for this run already shares —
   * makes exactly one of two racing callers at the ceiling win the reservation, with the other
   * observing the now-updated count and correctly failing the ceiling check.
   *
   * @param state        the run state
   * @param targetStepId the retried step's id
   * @param maxAttempts  the shared {@code RetryPolicy} ceiling
   *
   * @return {@code true} if the reservation was granted (the attempt is now recorded); {@code false}
   *     if the ceiling was already reached (state left untouched)
   */
  public static boolean reserve(WorkflowState state, String targetStepId, int maxAttempts) {
    synchronized (state) {
      int attempts = read(state, targetStepId);
      if (attempts >= maxAttempts) {
        return false;
      }
      state.putContextValue(key(targetStepId),
          new StringContextValue(String.valueOf(attempts + 1), ContextProvenance.SYSTEM_GENERATED));
      return true;
    }
  }

  private static String key(String targetStepId) {
    return KEY_PREFIX + targetStepId + KEY_SUFFIX;
  }
}
