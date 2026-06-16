// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

/**
 * Outcome of a single {@link FakeResponseSource#nextResponse(FakeInvocation)} call. A sealed result (rather than a bare
 * {@code Optional}) so the client can raise the two distinct fail-closed errors — "no script registered for this run"
 * versus "this key is absent from the run's script" — from one atomic call, with no time-of-check/time-of-use gap and
 * no second query method.
 */
public sealed interface FakeResolution
    permits FakeResolution.Found, FakeResolution.RunNotScripted, FakeResolution.KeyAbsent {

  /**
   * A scripted response was found for the invocation's key.
   *
   * @param response the scripted response; never {@code null}
   */
  record Found(FakeResponse response) implements FakeResolution {

  }

  /**
   * No script is registered for the invocation's run. The ordinal counter is not advanced in this case. A
   * {@link StaticFakeResponseSource} never returns this — it serves its single script for every run.
   */
  record RunNotScripted() implements FakeResolution {

  }

  /**
   * A script is registered for the run, but it has no entry for this key (the ordinal counter has already advanced past
   * the miss — acceptable, since a miss is a fail-closed terminal).
   *
   * @param key the key that had no scripted response
   */
  record KeyAbsent(FakeScriptKey key) implements FakeResolution {

  }
}
