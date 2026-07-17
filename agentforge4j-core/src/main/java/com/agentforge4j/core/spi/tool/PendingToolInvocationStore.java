// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import java.util.List;

/**
 * Run-scoped store of {@link PendingToolInvocation}s awaiting approval.
 *
 * <p>Lookups are always scoped by {@code runId}, so an id belonging to another run is never
 * returned.
 * An embedding application may additionally scope lookups within its own identity model.
 */
public interface PendingToolInvocationStore {

  /**
   * Persists a pending invocation.
   *
   * @param pending the pending invocation
   */
  void save(PendingToolInvocation pending);

  /**
   * Finds a pending invocation within its owning run.
   *
   * @param runId            owning run id
   * @param toolInvocationId pending invocation id
   *
   * @return the pending invocation, or {@code null} if it does not belong to {@code runId} or is
   * gone
   */
  PendingToolInvocation find(String runId, String toolInvocationId);

  /**
   * Returns every pending invocation currently awaiting resolution for {@code runId}. The result is
   * scoped to the run — invocations belonging to other runs are never included — and is empty when
   * the run has none pending. Used to resolve the single current pending invocation when a scripted
   * approve/deny/retry/continue does not name an id; callers fail closed when the count is not one.
   *
   * @param runId owning run id
   *
   * @return the run's pending invocations, in no guaranteed order; never {@code null}
   */
  List<PendingToolInvocation> findByRun(String runId);

  /**
   * Removes a pending invocation; a no-op if it is absent.
   *
   * @param runId            owning run id
   * @param toolInvocationId pending invocation id
   */
  void remove(String runId, String toolInvocationId);

  /**
   * Atomically removes and returns a pending invocation, but only if the row currently stored for
   * {@code runId}/{@code toolInvocationId} is still exactly {@code expectedPending} — the same
   * instance (or an equal value) an earlier {@link #find} on this call's behalf observed. This
   * closes two race windows at once: when two callers race to resume the same invocation, at most
   * one observes a non-{@code null} result and every other concurrent caller observes {@code null}
   * as though the row were already gone; and when the row is replaced (for example by a fresh
   * pending row persisted after a claimed resume itself failed) between one caller's {@link #find}
   * and its own claim attempt, that caller's claim fails rather than consuming the replacement
   * under the stale row's authorization.
   *
   * <p>This is the sole safe way to consume a pending invocation before invoking its provider: a
   * separate {@link #find} followed by a separate {@link #remove} leaves a window in which two
   * concurrent resumes both observe the row and both invoke the provider, or in which a caller
   * claims a row that is no longer the one it decided to act on. Implementations backed by a
   * durable or distributed store <strong>must</strong> provide this atomically — for example a
   * conditional delete tied to the expected row's identity/version (compare-and-delete), an
   * equivalent compare-and-swap, or a {@code SELECT ... FOR UPDATE} followed by a delete guarded by
   * the same condition within the same transaction — never a naive find-then-remove pair, which
   * would silently reopen the race this method exists to close.
   *
   * @param runId            owning run id
   * @param toolInvocationId pending invocation id
   * @param expectedPending  the pending invocation the caller observed and is claiming; the claim
   *                         only succeeds if the currently stored row still equals this value
   *
   * @return the claimed pending invocation, or {@code null} if it does not belong to {@code runId},
   * is already gone, was already claimed by a concurrent caller, or no longer equals
   * {@code expectedPending} (it was replaced)
   */
  PendingToolInvocation claim(String runId, String toolInvocationId,
      PendingToolInvocation expectedPending);
}
