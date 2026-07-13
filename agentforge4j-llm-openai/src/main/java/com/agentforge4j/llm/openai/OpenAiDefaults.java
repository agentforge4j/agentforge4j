// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.openai;

import java.time.Duration;

/**
 * Canonical connect/request timeout defaults for the openai provider, referenced by both
 * {@link OpenAiConfigurationAdapter} (the properties-configured path) and
 * {@link OpenAiNeutralConfiguration#fromNeutral} (the programmatic-construction path), so the two call sites cannot
 * silently diverge on what "the" default is.
 */
final class OpenAiDefaults {

  /** Default HTTP connect timeout when {@code connect-timeout} is not configured. */
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /** Default request timeout when {@code request-timeout} / the {@code request.timeout} option is not configured. */
  static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

  private OpenAiDefaults() {
  }
}
