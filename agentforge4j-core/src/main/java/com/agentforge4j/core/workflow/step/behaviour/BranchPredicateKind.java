// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

/**
 * The kind of match a {@link BranchPredicate} performs against a branch step's resolved context value. The set is
 * closed; an unrecognised kind fails to deserialize (and therefore fails load-time validation) rather than silently
 * matching nothing.
 */
public enum BranchPredicateKind {

  /**
   * Matches when the resolved value is a member of the predicate's {@code members} set.
   */
  MEMBER_OF,

  /**
   * Matches when the resolved value is blank (empty or whitespace).
   */
  EMPTY
}
