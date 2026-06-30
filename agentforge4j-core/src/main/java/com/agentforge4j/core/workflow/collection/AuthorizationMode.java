// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * How a collection gate authorizes operations.
 */
public enum AuthorizationMode {
  /**
   * Any non-blank actor may submit and view, and may mutate items they own; declared per-action
   * requirements are not evaluated. Mutating operations still fail closed on a missing actor.
   */
  OPEN,
  /**
   * Every guarded action requires a satisfied per-action requirement; a missing value, an unwired or
   * failing authorizer, or a blank actor denies the operation.
   */
  ENFORCED
}
