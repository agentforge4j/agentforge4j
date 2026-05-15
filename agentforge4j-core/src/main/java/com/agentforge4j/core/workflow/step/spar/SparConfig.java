package com.agentforge4j.core.workflow.step.spar;

import com.agentforge4j.util.Validate;

/**
 * Configuration for a spar (adversarial) step: challenger identity, round cap, and prompt used to
 * resolve the debate.
 *
 * @param challengerAgentId non-blank agent id for the challenger role
 * @param maxRounds         at least one round
 * @param resolutionPrompt  non-blank prompt text used after sparring completes
 */
public record SparConfig(
    String challengerAgentId,
    int maxRounds,
    String resolutionPrompt
) {

  public SparConfig {
    Validate.notBlank(challengerAgentId, "SparConfig challengerAgentId must not be blank");
    Validate.isGreaterThanZero(maxRounds, "SparConfig maxRounds must be at least 1");
    Validate.notBlank(resolutionPrompt, "SparConfig resolutionPrompt must not be blank");
  }
}
