// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.util.Validate;

/**
 * A request to close a collection gate.
 *
 * @param actorId    the opaque effective actor requesting the close; must not be blank
 * @param reason     why the gate is being closed; must not be {@code null} and must not be
 *                   {@link CloseReason#OVERRIDE} — that value is a derived outcome the gate records
 *                   when {@code override} bypasses an unmet constraint, never a requestable reason
 * @param override   when {@code true}, close even if a minimum-item constraint is unmet; requires the
 *                   {@code override} permission and records the unmet constraint
 * @param closeToken optional idempotency token. A repeated close carrying the same token is a no-op
 *                   only under {@link com.agentforge4j.core.workflow.collection.ReopenPolicy#ALLOWED},
 *                   where the gate stays {@code AWAITING_COLLECTION} after closing. Under
 *                   {@link com.agentforge4j.core.workflow.collection.ReopenPolicy#NONE}, a successful
 *                   close always advances the run past the gate, so a repeated call is rejected as an
 *                   invalid-status call rather than treated as idempotent — the token does not survive
 *                   that transition. {@code null} when absent
 */
public record CloseRequest(String actorId, CloseReason reason, boolean override, String closeToken) {

  public CloseRequest {
    Validate.notBlank(actorId, "CloseRequest actorId must not be blank");
    Validate.notNull(reason, "CloseRequest reason must not be null");
    Validate.isTrue(reason != CloseReason.OVERRIDE,
        "CloseRequest reason must not be OVERRIDE; it is a derived outcome, not a requestable reason");
  }
}
