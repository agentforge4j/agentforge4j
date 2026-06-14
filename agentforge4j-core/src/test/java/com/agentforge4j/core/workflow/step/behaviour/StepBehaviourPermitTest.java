package com.agentforge4j.core.workflow.step.behaviour;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code StepBehaviour} sealed hierarchy: the transition work adds the {@link TransitionAware} interface to
 * the five carriers without changing the permit set.
 */
class StepBehaviourPermitTest {

  @Test
  void permitSetIsUnchanged() {
    assertThat(StepBehaviour.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
        AgentBehaviour.class, SparBehaviour.class, WorkflowBehaviour.class, InputBehaviour.class,
        ResourceBehaviour.class, BranchBehaviour.class, FailBehaviour.class,
        RetryPreviousBehaviour.class);
  }

  @Test
  void transitionCarriersImplementTransitionAware() {
    assertThat(List.of(AgentBehaviour.class, InputBehaviour.class, ResourceBehaviour.class,
        SparBehaviour.class, WorkflowBehaviour.class))
        .allMatch(TransitionAware.class::isAssignableFrom);
  }

  @Test
  void nonCarriersAreNotTransitionAware() {
    assertThat(List.of(BranchBehaviour.class, FailBehaviour.class, RetryPreviousBehaviour.class))
        .noneMatch(TransitionAware.class::isAssignableFrom);
  }
}
