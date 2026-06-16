// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single per-run store for the fake provider: each run id maps to one entry holding the run's {@link FakeScript}
 * <em>and</em> its ordinal counters, so registering, deregistering, or evicting a run clears both in one place — there
 * is no separate counter map to keep in sync, and the {@link FakeLlmClient} stays stateless.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #register(String, FakeScript)} installs (or atomically replaces) a run's script,
 *       resetting that run's counters — a fresh script implies a fresh sequence.</li>
 *   <li>{@link #deregister(String)} removes the run's script and counters together.</li>
 *   <li>A TTL leak guard evicts runs whose last access is older than the configured TTL; it runs
 *       opportunistically when a new run is first seen (never on a hot read path) so an active
 *       long-running run — kept fresh by its own calls — is not evicted (no LRU on the leak
 *       guard).</li>
 *   <li>An optional max-run cap (dev/demo only) evicts the least-recently-accessed run when the cap
 *       is exceeded, always logging the eviction — never a silent drop.</li>
 * </ul>
 *
 * <p>Thread-safe: a {@link ConcurrentHashMap} of runs, per-run {@link ConcurrentHashMap} counters,
 * and {@link AtomicInteger#getAndIncrement()} ordinals. A single instance serves concurrent runs.
 */
public final class FakeRunLifecycle {

  private static final System.Logger LOG = System.getLogger(FakeRunLifecycle.class.getName());

  private final Map<String, RunEntry> runs = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Duration ttl;
  private final int maxRuns;

  /**
   * Creates a lifecycle with no TTL eviction and no run cap (system UTC clock).
   */
  public FakeRunLifecycle() {
    this(Clock.systemUTC(), null, 0);
  }

  /**
   * Creates a lifecycle with the given leak guards.
   *
   * @param clock   clock for last-access timestamps and TTL; must not be {@code null}
   * @param ttl     stale-run time-to-live, or {@code null}/zero/negative to disable TTL eviction
   * @param maxRuns optional max concurrent runs ({@code 0} disables the cap); dev/demo only
   */
  public FakeRunLifecycle(Clock clock, Duration ttl, int maxRuns) {
    this.clock = Validate.notNull(clock, "clock must not be null");
    this.ttl = ttl;
    this.maxRuns = Validate.isNotNegative(maxRuns, "maxRuns must be zero or greater").intValue();
  }

  /**
   * Installs (or atomically replaces) the script for a run, resetting that run's ordinal counters.
   *
   * @param runId  run id; must not be blank
   * @param script script to register; must not be {@code null}
   */
  public void register(String runId, FakeScript script) {
    Validate.notBlank(runId, "runId must not be blank");
    Validate.notNull(script, "script must not be null");
    sweepExpired();
    runs.put(runId, new RunEntry(script, nowMillis()));
    enforceCap(runId);
  }

  /**
   * Removes a run's script and ordinal counters together. No-op when the run is unknown.
   *
   * @param runId run id; must not be blank
   */
  public void deregister(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    runs.remove(runId);
  }

  /**
   * Runs the TTL leak guard immediately, evicting stale runs.
   */
  public void sweep() {
    sweepExpired();
  }

  /**
   * Returns whether a run currently has state (script and/or counters) tracked.
   *
   * @param runId run id; must not be blank
   *
   * @return {@code true} if the run is tracked
   */
  public boolean isRegistered(String runId) {
    Validate.notBlank(runId, "runId must not be blank");
    return runs.containsKey(runId);
  }

  /**
   * Resolves the next response for an invocation against an explicitly registered script. Returns
   * {@link FakeResolution.RunNotScripted} (without advancing any counter) when the run has no registered script. Used
   * by {@link RegistryFakeResponseSource}.
   */
  FakeResolution nextResponse(FakeInvocation invocation) {
    Validate.notNull(invocation, "invocation must not be null");
    RunEntry entry = runs.get(invocation.runId());
    if (entry == null) {
      return new FakeResolution.RunNotScripted();
    }
    return resolve(entry, invocation);
  }

  /**
   * Resolves the next response for an invocation, lazily creating the run's entry from {@code defaultScript} when
   * absent (so the run is never "unscripted"). Used by {@link StaticFakeResponseSource}; TTL and cap guards apply when
   * a new run first appears.
   */
  FakeResolution nextResponseWithDefault(FakeInvocation invocation, FakeScript defaultScript) {
    Validate.notNull(invocation, "invocation must not be null");
    Validate.notNull(defaultScript, "defaultScript must not be null");
    RunEntry existing = runs.get(invocation.runId());
    if (existing == null) {
      sweepExpired();
      existing = runs.computeIfAbsent(invocation.runId(),
          id -> new RunEntry(defaultScript, nowMillis()));
      enforceCap(invocation.runId());
    }
    return resolve(existing, invocation);
  }

  /**
   * Package-visible run-tracking probe for the no-leak lifecycle tests.
   */
  boolean containsRun(String runId) {
    return runs.containsKey(runId);
  }

  private FakeResolution resolve(RunEntry entry, FakeInvocation invocation) {
    entry.touch(nowMillis());
    int ordinal = entry.nextOrdinal(new OrdinalKey(
        invocation.runId(), invocation.workflowId(), invocation.stepId(), invocation.agentId()));
    FakeScriptKey key = new FakeScriptKey(
        invocation.workflowId(), invocation.stepId(), invocation.agentId(), ordinal);
    FakeResponse response = entry.script().responses().get(key);
    if (response == null) {
      return new FakeResolution.KeyAbsent(key);
    }
    return new FakeResolution.Found(response);
  }

  private void sweepExpired() {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      return;
    }
    long cutoff = nowMillis() - ttl.toMillis();
    runs.entrySet().removeIf(entry -> entry.getValue().lastAccessMillis() < cutoff);
  }

  private void enforceCap(String justAddedRunId) {
    if (maxRuns <= 0) {
      return;
    }
    while (runs.size() > maxRuns) {
      String oldest = runs.entrySet().stream()
          .filter(entry -> !entry.getKey().equals(justAddedRunId))
          .min(Comparator.comparingLong(entry -> entry.getValue().lastAccessMillis()))
          .map(Map.Entry::getKey)
          .orElse(null);
      if (oldest == null) {
        return;
      }
      runs.remove(oldest);
      LOG.log(System.Logger.Level.WARNING,
          "Fake run cap {0} exceeded; evicted least-recently-used run ''{1}''", maxRuns, oldest);
    }
  }

  private long nowMillis() {
    return clock.millis();
  }

  private static final class RunEntry {

    private final FakeScript script;
    private final Map<OrdinalKey, AtomicInteger> counters = new ConcurrentHashMap<>();
    private volatile long lastAccessMillis;

    private RunEntry(FakeScript script, long lastAccessMillis) {
      this.script = script;
      this.lastAccessMillis = lastAccessMillis;
    }

    private FakeScript script() {
      return script;
    }

    private long lastAccessMillis() {
      return lastAccessMillis;
    }

    private void touch(long millis) {
      this.lastAccessMillis = millis;
    }

    private int nextOrdinal(OrdinalKey key) {
      return counters.computeIfAbsent(key, ignored -> new AtomicInteger()).getAndIncrement();
    }
  }
}
