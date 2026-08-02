// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code StepBehaviour} sealed hierarchy: the permit set and the {@link TransitionAware} carrier split.
 * {@code AggregateBehaviour} is the twelfth permit and the seventh transition carrier; the deterministic
 * {@link ValidateBehaviour} and {@link AssignContextBehaviour} are permits but not transition carriers.
 */
class StepBehaviourPermitTest {

  @Test
  void permitSetMatchesExpected() {
    assertThat(StepBehaviour.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
        AgentBehaviour.class, SparBehaviour.class, WorkflowBehaviour.class, InputBehaviour.class,
        ResourceBehaviour.class, BranchBehaviour.class, FailBehaviour.class,
        RetryPreviousBehaviour.class, ValidateBehaviour.class, AssignContextBehaviour.class,
        CollectionBehaviour.class, AggregateBehaviour.class);
  }

  @Test
  void transitionCarriersImplementTransitionAware() {
    assertThat(List.of(AgentBehaviour.class, InputBehaviour.class, ResourceBehaviour.class,
        SparBehaviour.class, WorkflowBehaviour.class, CollectionBehaviour.class,
        AggregateBehaviour.class))
        .allMatch(TransitionAware.class::isAssignableFrom);
  }

  @Test
  void nonCarriersAreNotTransitionAware() {
    assertThat(List.of(BranchBehaviour.class, FailBehaviour.class, RetryPreviousBehaviour.class,
        ValidateBehaviour.class, AssignContextBehaviour.class))
        .noneMatch(TransitionAware.class::isAssignableFrom);
  }
}
