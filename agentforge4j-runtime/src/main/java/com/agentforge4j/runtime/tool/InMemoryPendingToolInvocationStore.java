// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.core.spi.tool.PendingToolInvocation;
import com.agentforge4j.core.spi.tool.PendingToolInvocationStore;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link PendingToolInvocationStore} (OSS default). Entries are keyed by run id and
 * invocation id so a lookup for one run can never return another run's pending invocation.
 */
public final class InMemoryPendingToolInvocationStore implements PendingToolInvocationStore {

  private final Map<Key, PendingToolInvocation> byRunAndId = new ConcurrentHashMap<>();

  @Override
  public void save(PendingToolInvocation pending) {
    Validate.notNull(pending, "pending must not be null");
    byRunAndId.put(key(pending.runId(), pending.toolInvocationId()), pending);
  }

  @Override
  public PendingToolInvocation find(String runId, String toolInvocationId) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    return byRunAndId.get(key(runId, toolInvocationId));
  }

  @Override
  public List<PendingToolInvocation> findByRun(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    return byRunAndId.values().stream()
        .filter(pending -> runId.equals(pending.runId()))
        .toList();
  }

  @Override
  public void remove(String runId, String toolInvocationId) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    byRunAndId.remove(key(runId, toolInvocationId));
  }

  @Override
  public PendingToolInvocation claim(String runId, String toolInvocationId,
      PendingToolInvocation expectedPending) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    Validate.notNull(expectedPending, "expectedPending must not be null");
    // ConcurrentHashMap.remove(key, value) is an atomic compare-and-delete: it removes and returns
    // true only if the currently mapped value equals expectedPending. Of any number of concurrent
    // callers racing the same key with the same expected row, exactly one observes true and every
    // other observes false, closing the find-then-remove race window this method exists to
    // eliminate; a caller whose expected row was replaced by a different row also observes false,
    // rather than claiming the replacement under the stale row's authorization.
    Key key = key(runId, toolInvocationId);
    return byRunAndId.remove(key, expectedPending) ? expectedPending : null;
  }

  @Override
  public PendingToolInvocation verifyStillCurrent(String runId, String toolInvocationId,
      PendingToolInvocation expectedPending) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notBlank(toolInvocationId, "toolInvocationId must not be blank");
    Validate.notNull(expectedPending, "expectedPending must not be null");
    // ConcurrentHashMap.get is linearizable with every put/remove on the same map, including
    // claim's own remove(key, value) and save's put — the same total order claim's compare-and-delete
    // already relies on for correctness. A true-equivalent result here is therefore a genuine,
    // race-free confirmation that expectedPending was still the current row at a single well-defined
    // point after every concurrent claim/save/remove that had already completed, not merely another
    // independently-timed read assembled from two separate calls.
    PendingToolInvocation current = byRunAndId.get(key(runId, toolInvocationId));
    return expectedPending.equals(current) ? expectedPending : null;
  }

  private static Key key(String runId, String toolInvocationId) {
    return new Key(runId, toolInvocationId);
  }

  private record Key(String runId, String toolInvocationId) {

  }
}
