// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code StepBehaviour} sealed hierarchy: {@link TransitionAware} carriers are the five
 * transition-carrying behaviours, and the permit set is the eight control/agent behaviours plus the
 * deterministic {@link ValidateBehaviour}.
 */
class StepBehaviourPermitTest {

  @Test
  void permitSetMatchesExpected() {
    assertThat(StepBehaviour.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
        AgentBehaviour.class, SparBehaviour.class, WorkflowBehaviour.class, InputBehaviour.class,
        ResourceBehaviour.class, BranchBehaviour.class, FailBehaviour.class,
        RetryPreviousBehaviour.class, ValidateBehaviour.class, AssignContextBehaviour.class);
  }

  @Test
  void transitionCarriersImplementTransitionAware() {
    assertThat(List.of(AgentBehaviour.class, InputBehaviour.class, ResourceBehaviour.class,
        SparBehaviour.class, WorkflowBehaviour.class))
        .allMatch(TransitionAware.class::isAssignableFrom);
  }

  @Test
  void nonCarriersAreNotTransitionAware() {
    assertThat(List.of(BranchBehaviour.class, FailBehaviour.class, RetryPreviousBehaviour.class,
        ValidateBehaviour.class, AssignContextBehaviour.class))
        .noneMatch(TransitionAware.class::isAssignableFrom);
  }
}
