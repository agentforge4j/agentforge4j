package com.agentforge4j.llm.fake;

import com.agentforge4j.util.Validate;

/**
 * A {@link FakeResponseSource} backed by a single, run-agnostic {@link FakeScript}: every run is served the same
 * script. For single-workflow dev and demos where there is no per-run registration.
 *
 * <p>Counters are still strictly run-scoped — each run id gets its own ordinal sequences and is
 * TTL-evicted — so repeated runs do not share an ever-climbing counter and the counter map does not leak. This source
 * therefore never reports {@link FakeResolution.RunNotScripted}: the run's entry is created on first use from the
 * single script.
 */
public final class StaticFakeResponseSource implements FakeResponseSource {

  private final FakeScript script;
  private final FakeRunLifecycle counters;

  /**
   * Creates a static source with default (no TTL, no cap) run-scoped counters.
   *
   * @param script the single script served to every run; must not be {@code null}
   */
  public StaticFakeResponseSource(FakeScript script) {
    this(script, new FakeRunLifecycle());
  }

  /**
   * Creates a static source whose run-scoped counters use the given lifecycle store (for TTL/cap leak guards). The
   * store is used only for counters and per-run TTL; scripts are not registered against it explicitly.
   *
   * @param script   the single script served to every run; must not be {@code null}
   * @param counters lifecycle store for run-scoped counters; must not be {@code null}
   */
  public StaticFakeResponseSource(FakeScript script, FakeRunLifecycle counters) {
    this.script = Validate.notNull(script, "script must not be null");
    this.counters = Validate.notNull(counters, "counters must not be null");
  }

  @Override
  public FakeResolution nextResponse(FakeInvocation invocation) {
    return counters.nextResponseWithDefault(invocation, script);
  }
}
