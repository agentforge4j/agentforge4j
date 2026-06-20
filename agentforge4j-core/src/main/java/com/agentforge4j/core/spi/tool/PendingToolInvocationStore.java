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
}
