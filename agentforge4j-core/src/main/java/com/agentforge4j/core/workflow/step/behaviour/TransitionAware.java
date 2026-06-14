package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.step.StepTransition;

/**
 * Implemented by step behaviours that carry a post-completion {@link StepTransition} gate. The runtime reads the
 * transition uniformly via {@code instanceof TransitionAware} at step completion to decide whether to advance
 * ({@link StepTransition#AUTO}) or suspend for a human gate ({@link StepTransition#HUMAN_REVIEW} /
 * {@link StepTransition#HUMAN_APPROVAL}).
 *
 * <p>This is an additive interface implemented alongside the sealed {@code StepBehaviour}
 * hierarchy; it does not change the {@code StepBehaviour} permit set.
 */
public interface TransitionAware {

  /**
   * The post-completion gate for the behaviour.
   *
   * @return the non-null transition
   */
  StepTransition transition();
}
