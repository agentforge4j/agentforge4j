package com.agentforge4j.core.spi.tool;

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
   * Removes a pending invocation; a no-op if it is absent.
   *
   * @param runId            owning run id
   * @param toolInvocationId pending invocation id
   */
  void remove(String runId, String toolInvocationId);
}
