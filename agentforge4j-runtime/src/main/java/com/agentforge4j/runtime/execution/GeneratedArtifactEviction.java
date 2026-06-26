// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution;

import com.agentforge4j.core.workflow.file.ArtifactDescriptor;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.runtime.GeneratedArtifactStore;
import com.agentforge4j.util.Validate;

/**
 * Shared rewind helper: evicts a run's captured artifact bytes for paths emitted at or after a rewind threshold, so the
 * two rewind chokepoints ({@code DefaultWorkflowRuntime.retry} and {@code RetryPreviousBehaviourHandler}) stay in sync.
 * Call before {@link WorkflowState#clearEntriesFromUid(int)} so the descriptors are still present to read the paths from.
 *
 * <p>Internal runtime helper (its package is not exported).
 */
public final class GeneratedArtifactEviction {

  private GeneratedArtifactEviction() {
  }

  /**
   * Removes from {@code store} the captured bytes of every artifact {@code state} emitted at or after {@code retryUid},
   * reading the paths from the run's generated-artifact descriptors.
   *
   * @param store    the run-scoped artifact store; must not be {@code null}
   * @param state    the run whose descriptors identify the paths to evict; must not be {@code null}
   * @param retryUid the rewind threshold; artifacts emitted at a uid &gt;= this value are evicted
   */
  public static void evictFromUid(GeneratedArtifactStore store, WorkflowState state, int retryUid) {
    Validate.notNull(store, "store must not be null");
    Validate.notNull(state, "state must not be null");
    String runId = state.getRunId();
    state.getGeneratedArtifactDescriptors().stream()
        .filter(descriptor -> descriptor.stepExecutionUid() >= retryUid)
        .map(ArtifactDescriptor::path)
        .forEach(path -> store.remove(runId, path));
  }
}
