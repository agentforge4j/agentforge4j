package com.agentforge4j.core.workflow.step.retry;

import com.agentforge4j.util.Validate;

/**
 * Flags and limits controlling whether a failed step may be retried, and what variations are
 * allowed.
 *
 * @param allowRetry             whether any retry is permitted
 * @param allowRetryFromPrevious whether retry may rewind to a prior step
 * @param allowAgentSwap         whether a different agent may be chosen on retry
 * @param allowPromptOverride    whether the step prompt may be changed on retry
 * @param maxAttempts            cap on attempts when any retry option is allowed; must be greater
 *                               than zero in that case
 */
public record RetryPolicy(
    boolean allowRetry,
    boolean allowRetryFromPrevious,
    boolean allowAgentSwap,
    boolean allowPromptOverride,
    int maxAttempts
) {

  public RetryPolicy {
    if (allowRetry || allowRetryFromPrevious || allowAgentSwap || allowPromptOverride) {
      Validate.isGreaterThanZero(maxAttempts,
          "RetryPolicy maxAttempts must be greater than zero if any retry option is allowed");
    }
  }

  /**
   * Returns a policy that disallows every retry option and sets {@code maxAttempts} to zero.
   */
  public static RetryPolicy none() {
    return new RetryPolicy(false, false, false, false, 0);
  }

  /**
   * Returns a policy that allows retry without rewind, agent swap, or prompt override.
   *
   * @param maxAttempts passed through to the record compact constructor (must be greater than zero)
   */
  public static RetryPolicy simple(int maxAttempts) {
    return new RetryPolicy(true, false, false, false, maxAttempts);
  }
}
