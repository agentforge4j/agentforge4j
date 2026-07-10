// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.util.Validate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 fingerprint of resolved source content, used to detect whether a compact sibling is stale
 * (source changed since compaction) and to key deterministic waste-detection signals. Content passed
 * here must already be canonical (stable regardless of incidental formatting) — see
 * {@link ContextSourceResolver}, which produces canonical text for JSON-shaped sources.
 */
public final class ContextFingerprint {

  private ContextFingerprint() {
  }

  /**
   * Computes the SHA-256 hex fingerprint of {@code content}.
   *
   * @param content the canonical content to fingerprint; must not be {@code null}
   *
   * @return lowercase hex-encoded SHA-256 digest; never {@code null}
   */
  public static String of(String content) {
    Validate.notNull(content, "content must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
