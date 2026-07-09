// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared SHA-256 hex digest of a string's UTF-8 bytes. The single home for the fingerprint/digest
 * helper previously duplicated across modules (context fingerprints, context-pack variant
 * fingerprints, generated-artifact descriptors, loop-list fingerprints).
 */
public final class Sha256 {

  private Sha256() {
  }

  /**
   * Computes the SHA-256 digest of {@code content}'s UTF-8 bytes.
   *
   * @param content the content to digest; must not be {@code null}
   *
   * @return lowercase hex-encoded SHA-256 digest; never {@code null}
   */
  public static String hex(String content) {
    Validate.notNull(content, "content must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
