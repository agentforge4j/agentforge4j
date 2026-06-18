// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small builder for the canonical dotted provider-option map emitted by the starter's configuration adapters. Null
 * values are skipped (so optional/unset properties become absent options that the provider factory defaults or
 * rejects), keeping the backing map free of the nulls that {@code LlmProviderOptions.of(...)} would reject.
 */
public final class NeutralOptions {

  private final Map<String, String> options = new LinkedHashMap<>();

  /**
   * @return a new builder
   */
  public static NeutralOptions create() {
    return new NeutralOptions();
  }

  /**
   * Adds a string option when {@code value} is non-null.
   *
   * @param key   the canonical option key
   * @param value the value, or {@code null} to skip
   *
   * @return {@code this}
   */
  public NeutralOptions string(String key, String value) {
    if (value != null) {
      options.put(key, value);
    }
    return this;
  }

  /**
   * Adds a {@link Duration} option (ISO-8601) when {@code value} is non-null.
   *
   * @param key   the canonical option key
   * @param value the value, or {@code null} to skip
   *
   * @return {@code this}
   */
  public NeutralOptions duration(String key, Duration value) {
    if (value != null) {
      options.put(key, value.toString());
    }
    return this;
  }

  /**
   * Adds a numeric option when {@code value} is non-null.
   *
   * @param key   the canonical option key
   * @param value the value, or {@code null} to skip
   *
   * @return {@code this}
   */
  public NeutralOptions number(String key, Number value) {
    if (value != null) {
      options.put(key, String.valueOf(value));
    }
    return this;
  }

  /**
   * @return the assembled option map
   */
  public Map<String, String> toMap() {
    return options;
  }
}
