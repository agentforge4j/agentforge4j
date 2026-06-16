// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * An immutable set of scripted responses for one run, keyed by {@link FakeScriptKey}. Built by {@link FakeScriptParser}
 * and registered against a run id via {@link FakeRunLifecycle}. On-the-fly mutability is atomic whole-script
 * replacement per run (re-registering a run with a fresh script, which also resets that run's ordinal counters) — never
 * in-place mutation of an existing script.
 *
 * @param schemaVersion script schema version this script was parsed under
 * @param responses     immutable map of key to scripted response
 */
public record FakeScript(int schemaVersion, Map<FakeScriptKey, FakeResponse> responses) {

  /**
   * Defensively copies {@code responses} into an immutable map.
   *
   * @throws IllegalArgumentException if {@code schemaVersion} is not positive or {@code responses} is {@code null}
   */
  public FakeScript {
    Validate.isGreaterThanZero(schemaVersion, "schemaVersion must be greater than zero");
    responses = Map.copyOf(Validate.notNull(responses, "responses must not be null"));
  }
}
