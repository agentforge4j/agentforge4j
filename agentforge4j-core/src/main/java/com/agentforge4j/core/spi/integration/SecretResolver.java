// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.integration;

/**
 * Resolves a secret-reference key to its secret value at the point of use.
 *
 * <p>Integration configuration never carries plaintext credentials: a {@code secretHeaders} entry
 * (or any other secret slot) holds a <em>reference</em> key, and this resolver turns that key into the live value when
 * the integration is invoked. The OSS default ({@link EnvironmentSecretResolver}) reads process environment variables
 * and system properties; an embedding application supplies its own implementation backed by
 * its secret store.
 *
 * <p>Resolution is deliberately lazy — performed per invocation, not at wiring time — so an unused
 * capability whose secret is absent does not block startup. A reference that cannot be resolved is a fail-fast error.
 * Implementations must never include a resolved value in any log, exception message, or other output.
 */
public interface SecretResolver {

  /**
   * Resolves the given secret-reference key to its secret value.
   *
   * @param reference the secret-reference key; must not be blank
   *
   * @return the resolved secret value; never blank
   *
   * @throws IllegalArgumentException if {@code reference} is blank or no value is configured for it
   */
  String resolve(String reference);
}
