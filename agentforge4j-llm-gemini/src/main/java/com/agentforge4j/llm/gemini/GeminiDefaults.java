// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import java.time.Duration;

/**
 * Canonical connect/request timeout defaults for the gemini provider, referenced by both
 * {@link GeminiConfigurationAdapter} (the properties-configured path) and
 * {@link GeminiNeutralConfiguration#fromNeutral} (the programmatic-construction path), so the two call sites cannot
 * silently diverge on what "the" default is.
 */
final class GeminiDefaults {

  /** Default HTTP connect timeout when {@code connect-timeout} is not configured. */
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /** Default request timeout when {@code request-timeout} / the {@code request.timeout} option is not configured. */
  static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

  private GeminiDefaults() {
  }
}
