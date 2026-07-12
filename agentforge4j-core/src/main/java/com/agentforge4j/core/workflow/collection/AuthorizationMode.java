// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * How a collection gate authorizes operations.
 */
public enum AuthorizationMode {
  /**
   * Any non-blank actor may submit and view, may mutate items they own, and may close or reopen
   * the gate itself; declared per-action requirements are not evaluated. {@code REPLACE_ANY},
   * {@code WITHDRAW_ANY}, {@code DEADLINE_CLOSE}, and {@code OVERRIDE} are always denied under
   * this mode regardless of ownership — switch to {@link #ENFORCED} to authorize those, or to
   * restrict who may close/reopen. Mutating operations still fail closed on a missing actor.
   */
  OPEN,
  /**
   * Every guarded action requires a satisfied per-action requirement; a missing value, an unwired or
   * failing authorizer, or a blank actor denies the operation.
   */
  ENFORCED
}
