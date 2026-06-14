package com.agentforge4j.core.workflow.state;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Failure details attached to {@link WorkflowState} when a run ends unsuccessfully.
 *
 * <p>The {@code kind} discriminator lets a serializing consumer (for example a run-state
 * snapshot) round-trip the concrete variant; it is not interpreted by the framework.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", defaultImpl = RunFailure.ExceptionFailure.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RunFailure.ExceptionFailure.class, name = "EXCEPTION"),
    @JsonSubTypes.Type(value = RunFailure.StepRejectionFailure.class, name = "STEP_REJECTION")
})
public sealed interface RunFailure permits RunFailure.ExceptionFailure, RunFailure.StepRejectionFailure {

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
  @JsonIgnoreProperties(ignoreUnknown = true)
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

  /**
   * Failure caused by a human rejecting a {@code HUMAN_APPROVAL} step gate (no send-back).
   *
   * @param failureReason non-blank rejection reason
   * @param failedStepId  non-blank id of the rejected step
   * @param supportId     non-blank id for support or log correlation
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record StepRejectionFailure(
      String failureReason,
      String failedStepId,
      String supportId
  ) implements RunFailure {

    public StepRejectionFailure {
      Validate.notBlank(failureReason, "failureReason must not be blank");
      Validate.notBlank(failedStepId, "failedStepId must not be blank");
      Validate.notBlank(supportId, "supportId must not be blank");
    }
  }
}
