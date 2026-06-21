// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.catalog;

/**
 * Thrown at load time when a shipped workflow catalog is present on the classpath but is
 * incompatible with the running framework: its manifest is missing or unparseable, the framework
 * version falls outside the catalog's declared bounds, or its workflow schema version is
 * unsupported.
 */
public final class CatalogCompatibilityException extends RuntimeException {

  /**
   * Creates a compatibility exception.
   *
   * @param message human-readable reason the catalog was rejected
   */
  public CatalogCompatibilityException(String message) {
    super(message);
  }

  /**
   * Creates a compatibility exception with an underlying cause.
   *
   * @param message human-readable reason the catalog was rejected
   * @param cause   the underlying failure (e.g. a manifest parse error)
   */
  public CatalogCompatibilityException(String message, Throwable cause) {
    super(message, cause);
  }
}
