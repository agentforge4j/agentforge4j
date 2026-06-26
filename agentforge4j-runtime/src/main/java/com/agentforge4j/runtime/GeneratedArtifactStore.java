// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Run-scoped, transient holder for the bytes a run emits via {@code CREATE_FILE}, so a later deterministic step
 * (artifact validation) can read the authoritative emitted content in-process — never from disk and never from an
 * LLM-echoed copy.
 *
 * <p>Contract:
 * <ul>
 *   <li><b>Run isolation</b> — entries are keyed by {@code runId}; a run never observes another
 *       run's artifacts.</li>
 *   <li><b>Last-write-wins</b> — registering a {@code path} already present for a {@code runId}
 *       replaces its content (honouring the {@code CREATE_FILE} overwrite contract); a rewind that
 *       re-emits a captured path upserts rather than failing.</li>
 *   <li><b>Bounds</b> — implementations enforce configured limits and reject writes that exceed
 *       them, failing closed rather than growing without limit. The per-run artifact-count bound is
 *       evaluated only when registering a <em>new</em> path; an upsert of an existing path does not
 *       count against it.</li>
 *   <li><b>Eviction</b> — {@link #remove(String, String)} drops a single captured path (used when a
 *       retry/rewind clears the emitting step's execution range).</li>
 *   <li><b>Lifecycle</b> — {@link #clear(String)} releases a run's artifacts when the run reaches a
 *       terminal state. The store is <em>transient</em>: bytes are not guaranteed to survive a
 *       suspend/resume (a distributed or restarted resume loses them), so validation must run in the
 *       same drive as capture; the persisted record is the
 *       {@link com.agentforge4j.core.workflow.file.ArtifactDescriptor} set on the run state. A rewind
 *       landing between two writes of the same captured path evicts that path rather than restoring
 *       the earlier write — capture feeds a same-drive {@code VALIDATE} step, not general file history.</li>
 * </ul>
 *
 * <p>The default {@link InMemoryGeneratedArtifactStore} retains bytes in memory; embedding
 * applications may supply an alternative implementation.
 */
public interface GeneratedArtifactStore {

  /**
   * Registers the emitted bytes of a file produced by a step for the given run.
   *
   * @param runId   non-blank owning run id
   * @param stepId  non-blank producing step id
   * @param path    non-blank requested artifact path
   * @param content emitted content; must not be {@code null}
   *
   * @throws IllegalArgumentException if a configured bound would be exceeded, or an argument is invalid. Re-registering
   *                                  an already-present {@code path} is not an error — it replaces the prior content
   *                                  (last-write-wins).
   */
  void register(String runId, String stepId, String path, String content);

  /**
   * Returns the emitted content for {@code path} within {@code runId}, if present.
   *
   * @param runId non-blank run id
   * @param path  non-blank artifact path
   *
   * @return the content, or empty when no artifact was registered at that path for the run
   */
  Optional<String> find(String runId, String path);

  /**
   * Returns the artifacts registered for {@code runId}, in registration order.
   *
   * @param runId non-blank run id
   *
   * @return an immutable snapshot (empty when the run has no registered artifacts)
   */
  List<GeneratedArtifact> artifacts(String runId);

  /**
   * Removes the artifact at {@code path} for {@code runId}, if present. Idempotent (a no-op when the run or path is
   * absent).
   *
   * @param runId non-blank run id
   * @param path  non-blank artifact path to evict
   */
  void remove(String runId, String path);

  /**
   * Releases all artifacts held for {@code runId}. Idempotent.
   *
   * @param runId non-blank run id
   */
  void clear(String runId);
}
