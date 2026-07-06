// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.runtime;

/**
 * Outcome of a close request on a collection gate.
 *
 * @param closed          whether the gate is closed after this call (true for a successful or
 *                        idempotent close, false when the close was rejected)
 * @param advanced        whether the run advanced past the gate as part of this call (true when the
 *                        reopen policy is {@code NONE}; false when the gate holds the run open for a
 *                        possible reopen until an explicit continuation)
 * @param finalCount      number of live (non-withdrawn) items at close; {@code 0} when rejected
 * @param rejectionReason reason the close was rejected; {@code null} when {@code closed}
 */
public record CloseResult(boolean closed, boolean advanced, int finalCount, String rejectionReason) {
}
