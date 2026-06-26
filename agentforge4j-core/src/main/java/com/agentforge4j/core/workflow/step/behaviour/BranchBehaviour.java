// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.Executable;

import com.agentforge4j.util.Validate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Conditional routing: chooses one {@link com.agentforge4j.core.workflow.Executable} branch using the value at
 * {@code contextKey}.
 *
 * <p>Resolution order (never by entry ordering of {@code branches}):
 * <ol>
 *   <li><b>Ordered predicates</b> — {@code predicates} are evaluated in list order; the first match
 *       wins. A matched predicate's {@code target} runs; a {@code null} target completes the step.</li>
 *   <li><b>Exact match</b> — {@code branches} contains the resolved value as a key. The mapped
 *       executable runs; a {@code null} value is a deliberate "matched, complete" route.</li>
 *   <li><b>Default</b> — when nothing above matches, {@code defaultBranch} runs when present.</li>
 *   <li><b>Unmatched</b> — when no predicate, no exact key, and no {@code defaultBranch} route the
 *       value: the step <b>fails closed</b> when {@code failOnUnmatched} is {@code true}, otherwise it
 *       completes and continues.</li>
 * </ol>
 *
 * @param contextKey      key whose string value selects the branch
 * @param branches        exact-match map from value to executable; a {@code null} value is a valid "matched, complete"
 *                        route
 * @param predicates      ordered predicates evaluated before {@code branches}; may be empty
 * @param defaultBranch   executable used when nothing else matches; may be {@code null}
 * @param failOnUnmatched when {@code true}, an unmatched value with no {@code defaultBranch} fails the run instead of
 *                        silently completing
 */
public record BranchBehaviour(
    String contextKey,
    Map<String, Executable> branches,
    List<BranchPredicate> predicates,
    Executable defaultBranch,
    boolean failOnUnmatched
) implements StepBehaviour {

  public BranchBehaviour {
    Validate.notBlank(contextKey, "context key for BranchBehaviour cannot be blank");
    // A branch value may be null ("matched, complete"), so the map is kept as-is rather than
    // Map.copyOf'd; a null map (predicate-only routing in JSON) defaults to empty.
    branches = branches == null ? Map.of() : branches;
    predicates = predicates == null ? List.of() : List.copyOf(predicates);
    Validate.isTrue(!branches.isEmpty() || !predicates.isEmpty(),
        "BranchBehaviour requires at least one branch or predicate");
  }

  /**
   * Returns every non-null child executable this branch can route to — exact-match branch targets, predicate targets,
   * and the default branch — as the single source of truth so reachability walkers (reference collection, validation)
   * descend predicate targets without each having to re-enumerate the branch's children.
   *
   * @return immutable list of routable child executables (null "matched, complete" routes excluded)
   */
  public List<Executable> childExecutables() {
    List<Executable> children = new ArrayList<>();
    branches.values().stream().filter(Objects::nonNull).forEach(children::add);
    predicates.stream().map(BranchPredicate::target).filter(Objects::nonNull).forEach(children::add);
    if (defaultBranch != null) {
      children.add(defaultBranch);
    }
    return List.copyOf(children);
  }
}
