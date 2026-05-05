package com.agentforge4j.core.workflow.step.behaviour;

/**
 * Terminal behaviour that forces step failure with the supplied {@code reason} (not validated
 * here).
 */
public record FailBehaviour(
    String reason
) implements StepBehaviour {

}
