// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

/**
 * A resolved LLM provider credential. Holds the live secret value behind an accessor and renders a redacted
 * {@code toString()} so the value never leaks into logs, events, exception messages, or other diagnostics.
 *
 * <p>Deliberately a final class rather than a record: a record's generated {@code toString()} would
 * print every component, exposing the credential. Providers hold credentials as {@code LlmSecret}, so a record-typed
 * provider configuration remains {@code toString}-safe.
 */
public final class LlmSecret {

  private final String value;

  /**
   * Creates a resolved secret.
   *
   * @param value the live secret value; must not be blank
   *
   * @throws IllegalArgumentException if {@code value} is blank
   */
  public LlmSecret(String value) {
    this.value = Validate.notBlank(value, "LlmSecret value must not be blank");
  }

  /**
   * Returns the live secret value. Callers must never log it or include it in diagnostics.
   *
   * @return the secret value; never blank
   */
  public String value() {
    return value;
  }

  @Override
  public String toString() {
    return "LlmSecret[REDACTED]";
  }
}
