// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.Executable;

import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * Conditional routing: chooses one {@link com.agentforge4j.core.workflow.Executable} branch using
 * the value at {@code contextKey}.
 *
 * <p>Routing contract (determined by key presence, never by entry ordering):
 * <ul>
 *   <li><b>Explicit match</b> — {@code branches} contains the resolved value as a key. The mapped
 *       executable runs; a key mapped to {@code null} is a deliberate "matched, no sub-executable"
 *       route that completes the branch step and continues. An explicit match never falls through
 *       to {@code defaultBranch}.</li>
 *   <li><b>Unmatched value</b> — the resolved value is absent from {@code branches}. The
 *       {@code defaultBranch} runs when present; a {@code null} default completes the branch step
 *       and continues (no fallback configured).</li>
 * </ul>
 *
 * @param contextKey    key whose string value selects the branch id in {@code branches}
 * @param branches      map from branch id to executable; a {@code null} value is a valid
 *                      "matched, complete" route. Not validated in this type
 * @param defaultBranch executable used when the context value matches no key; may be {@code null}
 *                      if unused
 */
public record BranchBehaviour(
    String contextKey,
    Map<String, Executable> branches,
    Executable defaultBranch
) implements StepBehaviour {

  public BranchBehaviour {
    Validate.notBlank(contextKey, "context key for BranchBehaviour cannot be blank");
    Validate.notNull(branches, "branches for BranchBehaviour cannot be null");
    Validate.notEmpty(branches.keySet(), "branches for BranchBehaviour cannot be empty");
  }
}
