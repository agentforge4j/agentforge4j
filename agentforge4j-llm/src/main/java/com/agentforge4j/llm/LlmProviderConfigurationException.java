// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

/**
 * Thrown for provider configuration-time failures: an unknown provider, a missing or invalid provider option, an
 * unresolvable credential reference, or a duplicate provider contributor.
 *
 * <p>Distinct from the runtime {@link com.agentforge4j.llm.api.LlmInvocationException}. Messages are
 * secret-safe by construction — callers name the offending provider and option/scheme key, never a credential value.
 */
public class LlmProviderConfigurationException extends RuntimeException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message; must not contain a credential value
   */
  public LlmProviderConfigurationException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and cause.
   *
   * @param message the detail message; must not contain a credential value
   * @param cause   the underlying cause
   */
  public LlmProviderConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
