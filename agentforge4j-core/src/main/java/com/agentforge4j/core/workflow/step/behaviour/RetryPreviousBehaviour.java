// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.util.Validate;

/**
 * Retries prior execution according to {@link RetryMode}, up to {@code maxAttempts}, optionally
 * delegating to {@code fallback}.
 *
 * @param retryStepId step that becomes the retry pivot; semantics depend on the runtime
 * @param retryMode   scope of the retry rewind or replay
 * @param maxAttempts maximum retry attempts (runtime defines counting boundaries)
 * @param fallback    executable invoked when retries are exhausted; may be {@code null} if unused
 */
public record RetryPreviousBehaviour(
    String retryStepId,
    RetryMode retryMode,
    int maxAttempts,
    Executable fallback
) implements StepBehaviour {

  public RetryPreviousBehaviour {
    Validate.notBlank(retryStepId, "retryStepId must not be blank for RetryPreviousBehaviour");
    Validate.notNull(retryMode,
        "retryMode must not be null for RetryPreviousBehaviour with retryStepId: %s".formatted(
            retryStepId));
    Validate.isGreaterThanZero(maxAttempts,
        "maxAttempts must be greater than zero for RetryPreviousBehaviour with retryStepId: %s".formatted(
            retryStepId));
  }
}
