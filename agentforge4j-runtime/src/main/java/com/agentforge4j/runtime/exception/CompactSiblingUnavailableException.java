// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.exception;

import com.agentforge4j.util.Validate;

/**
 * Thrown when a {@code COMPACT_ONLY} context selector's compact sibling is absent or stale (its
 * fingerprint does not match the current source). {@code COMPACT_ONLY} never falls back to the full
 * source — this is the controlled, fail-closed failure the design calls for.
 */
public final class CompactSiblingUnavailableException extends RuntimeException {

  private final String sourceId;
  private final String expectedFingerprint;
  private final String actualFingerprint;

  public CompactSiblingUnavailableException(String sourceId, String expectedFingerprint,
      String actualFingerprint) {
    super(("Compact sibling for source '%s' is unavailable or stale (COMPACT_ONLY): "
        + "expected fingerprint %s, actual %s").formatted(
        Validate.notBlank(sourceId, "sourceId"), expectedFingerprint, actualFingerprint));
    this.sourceId = sourceId;
    this.expectedFingerprint = expectedFingerprint;
    this.actualFingerprint = actualFingerprint;
  }

  public String sourceId() {
    return sourceId;
  }

  public String expectedFingerprint() {
    return expectedFingerprint;
  }

  public String actualFingerprint() {
    return actualFingerprint;
  }
}
