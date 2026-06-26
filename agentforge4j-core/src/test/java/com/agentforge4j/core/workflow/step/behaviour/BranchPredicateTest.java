// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BranchPredicateTest {

  @Test
  void member_of_requires_non_empty_members() {
    assertThatThrownBy(() -> new BranchPredicate(BranchPredicateKind.MEMBER_OF, Set.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MEMBER_OF requires a non-empty members set");
  }

  @Test
  void empty_predicate_allows_empty_members_and_null_target() {
    BranchPredicate predicate = new BranchPredicate(BranchPredicateKind.EMPTY, Set.of(), null);

    assertThat(predicate.kind()).isEqualTo(BranchPredicateKind.EMPTY);
    assertThat(predicate.members()).isEmpty();
    assertThat(predicate.target()).isNull();
  }

  @Test
  void null_kind_is_rejected() {
    assertThatThrownBy(() -> new BranchPredicate(null, Set.of("x"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind must not be null");
  }

  @Test
  void members_are_defensively_copied() {
    BranchPredicate predicate =
        new BranchPredicate(BranchPredicateKind.MEMBER_OF, Set.of("A", "B"), null);

    assertThatThrownBy(() -> predicate.members().add("C"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void branch_behaviour_requires_a_branch_or_a_predicate() {
    assertThatThrownBy(() -> new BranchBehaviour("k", Map.of(), List.of(), null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one branch or predicate");
  }

  @Test
  void branch_behaviour_accepts_predicate_only_routing() {
    BranchBehaviour behaviour = new BranchBehaviour("k", Map.of(),
        List.of(new BranchPredicate(BranchPredicateKind.EMPTY, Set.of(), null)), null, true);

    assertThat(behaviour.predicates()).hasSize(1);
    assertThat(behaviour.failOnUnmatched()).isTrue();
  }
}
