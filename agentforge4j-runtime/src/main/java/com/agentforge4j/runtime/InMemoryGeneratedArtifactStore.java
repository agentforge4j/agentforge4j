// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.util.Validate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link GeneratedArtifactStore} retaining emitted bytes in memory, keyed by run. Enforces run isolation,
 * last-write-wins upsert, and configured per-run bounds; {@link #clear(String)} releases a run's entries at terminal
 * state.
 *
 * <p>Bounds fail closed: a write that would exceed {@code maxArtifactsPerRun} or whose content
 * exceeds {@code maxContentLength} is rejected rather than retained, so a misbehaving run cannot grow the store without
 * limit.
 */
public final class InMemoryGeneratedArtifactStore implements GeneratedArtifactStore {

  /**
   * Default maximum number of artifacts retained per run.
   */
  public static final int DEFAULT_MAX_ARTIFACTS_PER_RUN = 256;
  /**
   * Default maximum content length (characters) retained per artifact.
   */
  public static final int DEFAULT_MAX_CONTENT_LENGTH = 1_000_000;

  private final int maxArtifactsPerRun;
  private final int maxContentLength;
  private final Map<String, Map<String, GeneratedArtifact>> artifactsByRun = new ConcurrentHashMap<>();

  /**
   * Creates a store with the default bounds.
   */
  public InMemoryGeneratedArtifactStore() {
    this(DEFAULT_MAX_ARTIFACTS_PER_RUN, DEFAULT_MAX_CONTENT_LENGTH);
  }

  /**
   * Creates a store with explicit bounds.
   *
   * @param maxArtifactsPerRun maximum artifacts retained per run; must be greater than zero
   * @param maxContentLength   maximum content length (characters) per artifact; must be greater than zero
   */
  public InMemoryGeneratedArtifactStore(int maxArtifactsPerRun, int maxContentLength) {
    this.maxArtifactsPerRun =
        Validate.isGreaterThanZero(maxArtifactsPerRun, "maxArtifactsPerRun must be at least 1")
            .intValue();
    this.maxContentLength =
        Validate.isGreaterThanZero(maxContentLength, "maxContentLength must be at least 1")
            .intValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void register(String runId, String stepId, String path, String content) {
    String validatedRunId = Validate.notBlank(runId, "runId must not be blank");
    GeneratedArtifact artifact = new GeneratedArtifact(stepId, path, content);
    Validate.isTrue(content.length() <= maxContentLength,
        "Generated artifact '%s' for run '%s' exceeds max content length %d"
            .formatted(path, validatedRunId, maxContentLength));
    Map<String, GeneratedArtifact> runArtifacts =
        artifactsByRun.computeIfAbsent(validatedRunId, key -> new LinkedHashMap<>());
    synchronized (runArtifacts) {
      // Last-write-wins: re-registering an existing path replaces its content and does not count
      // against the per-run artifact-count bound, which is enforced only for a genuinely new path.
      if (!runArtifacts.containsKey(path)) {
        Validate.isTrue(runArtifacts.size() < maxArtifactsPerRun,
            "Run '%s' exceeded max generated artifacts %d".formatted(validatedRunId,
                maxArtifactsPerRun));
      }
      runArtifacts.put(path, artifact);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Optional<String> find(String runId, String path) {
    Validate.notBlank(path, "path must not be blank");
    Map<String, GeneratedArtifact> runArtifacts =
        artifactsByRun.get(Validate.notBlank(runId, "runId must not be blank"));
    if (runArtifacts == null) {
      return Optional.empty();
    }
    synchronized (runArtifacts) {
      return Optional.ofNullable(runArtifacts.get(path)).map(GeneratedArtifact::content);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<GeneratedArtifact> artifacts(String runId) {
    Map<String, GeneratedArtifact> runArtifacts =
        artifactsByRun.get(Validate.notBlank(runId, "runId must not be blank"));
    if (runArtifacts == null) {
      return List.of();
    }
    synchronized (runArtifacts) {
      return List.copyOf(runArtifacts.values());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void remove(String runId, String path) {
    Validate.notBlank(path, "path must not be blank");
    Map<String, GeneratedArtifact> runArtifacts =
        artifactsByRun.get(Validate.notBlank(runId, "runId must not be blank"));
    if (runArtifacts == null) {
      return;
    }
    synchronized (runArtifacts) {
      runArtifacts.remove(path);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clear(String runId) {
    artifactsByRun.remove(Validate.notBlank(runId, "runId must not be blank"));
  }
}
