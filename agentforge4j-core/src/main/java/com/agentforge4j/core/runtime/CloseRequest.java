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
 * @param override   when {@code true}, close even if a minimum-item constraint is unmet. The
 *                   {@code override} permission is required only when the minimum is actually
 *                   unmet at close time — a close that would have succeeded anyway is not blocked
 *                   by a defensively-set flag. An override that was actually needed also records
 *                   the unmet constraint
 * @param closeToken optional idempotency token. Any authorized close call arriving while the gate is
 *                   already {@code CLOSED} and not yet reopened is a no-op, independent of whether a
 *                   token is supplied or whether it matches one seen before. The token specifically
 *                   covers the cross-cycle replay case under
 *                   {@link com.agentforge4j.core.workflow.collection.ReopenPolicy#ALLOWED}: a repeated
 *                   close carrying the same token as a close from a previous open/close cycle is also
 *                   treated as a no-op, where the gate stays {@code AWAITING_COLLECTION} after closing.
 *                   Under {@link com.agentforge4j.core.workflow.collection.ReopenPolicy#NONE}, a
 *                   successful close always advances the run past the gate, so a repeated call is
 *                   rejected as an invalid-status call rather than treated as idempotent — the token
 *                   does not survive that transition. {@code null} when absent
 */
public record CloseRequest(String actorId, CloseReason reason, boolean override, String closeToken) {

  public CloseRequest {
    Validate.notBlank(actorId, "CloseRequest actorId must not be blank");
    Validate.notNull(reason, "CloseRequest reason must not be null");
    Validate.isTrue(reason != CloseReason.OVERRIDE,
        "CloseRequest reason must not be OVERRIDE; it is a derived outcome, not a requestable reason");
  }
}
