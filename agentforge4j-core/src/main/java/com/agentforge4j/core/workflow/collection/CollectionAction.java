// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * The guarded operations on a collection gate. Each maps to an opaque wire name used as the
 * {@code action} of a {@code STEP_ACTION} {@code WorkflowRequirement} and matched by a
 * {@link CollectionAuthorizer}.
 */
public enum CollectionAction {
  SUBMIT("submit"),
  VIEW("view"),
  REPLACE_OWN("replace_own"),
  REPLACE_ANY("replace_any"),
  WITHDRAW_OWN("withdraw_own"),
  WITHDRAW_ANY("withdraw_any"),
  CLOSE("close"),
  REOPEN("reopen"),
  OVERRIDE("override"),
  DEADLINE_CLOSE("deadline_close");

  private final String wire;

  CollectionAction(String wire) {
    this.wire = wire;
  }

  /**
   * @return the stable wire name used in {@code STEP_ACTION} requirement {@code action} fields
   */
  public String wire() {
    return wire;
  }
}
