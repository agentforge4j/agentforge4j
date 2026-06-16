// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

import com.agentforge4j.core.workflow.Executable;

import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * Conditional routing: chooses one {@link com.agentforge4j.core.workflow.Executable} branch using
 * the value at {@code contextKey}, or {@code defaultBranch} when no map entry matches.
 *
 * @param contextKey    key whose string value selects the branch id in {@code branches}
 * @param branches      map from branch id to executable; not validated in this type
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
