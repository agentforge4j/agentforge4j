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

  private static Key key(String runId, String toolInvocationId) {
    return new Key(runId, toolInvocationId);
  }

  private record Key(String runId, String toolInvocationId) {

  }
}
