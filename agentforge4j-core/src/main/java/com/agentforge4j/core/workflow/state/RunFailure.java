package com.agentforge4j.core.workflow.state;

import com.agentforge4j.util.Validate;

/**
 * Failure details attached to {@link WorkflowState} when a run ends unsuccessfully.
 */
public sealed interface RunFailure permits RunFailure.ExceptionFailure {

  String failureReason();

  String failedStepId();

  String supportId();

  /**
   * Failure caused by an exception or runtime error with a support correlation id.
   *
   * @param failureReason non-blank human-readable explanation
   * @param failedStepId  step id where failure was observed; may be blank if unknown
   * @param supportId     non-blank id for support or log correlation
   */
  record ExceptionFailure(
      String failureReason,
      String failedStepId,
      String supportId
  ) implements RunFailure {

    public ExceptionFailure {
      Validate.notBlank(failureReason, "failureReason must not be blank");
      Validate.notBlank(supportId, "supportId must not be blank");
    }
  }
}
