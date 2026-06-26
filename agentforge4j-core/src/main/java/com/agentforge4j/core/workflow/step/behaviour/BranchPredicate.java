// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.Executable;
import com.agentforge4j.util.Validate;
import java.util.Set;

/**
 * One ordered predicate evaluated by a {@link BranchBehaviour} before its exact-match {@code branches}. When the
 * predicate matches the resolved context value, its {@code target} runs; a {@code null} target is a deliberate
 * "matched, complete, continue" route (mirroring a {@code null} branch value).
 *
 * @param kind    the match kind; must not be {@code null} (an unknown kind fails to deserialize)
 * @param members membership set for {@link BranchPredicateKind#MEMBER_OF} (required non-empty for that kind; ignored
 *                for {@link BranchPredicateKind#EMPTY})
 * @param target  executable to run when matched, or {@code null} to complete the branch step
 */
public record BranchPredicate(BranchPredicateKind kind, Set<String> members, Executable target) {

  public BranchPredicate {
    Validate.notNull(kind, "BranchPredicate kind must not be null");
    members = members == null ? Set.of() : Set.copyOf(members);
    if (kind == BranchPredicateKind.MEMBER_OF) {
      Validate.notEmpty(members, "BranchPredicate MEMBER_OF requires a non-empty members set");
    }
  }
}
