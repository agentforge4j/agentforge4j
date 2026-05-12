package com.agentforge4j.runtime.exception;

import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.util.Validate;

/**
 * Thrown when an agent step emits more blocking {@code USER_PROMPT} rounds than
 * {@link StepDefinition#maxUserPromptRounds()} allows.
 */
public final class UserPromptLimitExceededException extends RuntimeException {

  private final String stepId;
  private final int pauseCount;
  private final int maxRounds;

  public UserPromptLimitExceededException(String stepId, int pauseCount, int maxRounds) {
    super("Step '%s' exceeded maxUserPromptRounds (%d); blocking user prompts already shown: %d"
        .formatted(Validate.notBlank(stepId, "stepId"), maxRounds, pauseCount));
    this.stepId = stepId;
    this.pauseCount = pauseCount;
    this.maxRounds = maxRounds;
  }

  public String stepId() {
    return stepId;
  }

  public int pauseCount() {
    return pauseCount;
  }

  public int maxRounds() {
    return maxRounds;
  }
}
