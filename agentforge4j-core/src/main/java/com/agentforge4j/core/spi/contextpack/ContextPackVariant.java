// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.contextpack;

import com.agentforge4j.util.Validate;

/**
 * One loaded variant of a {@link ContextPack} (for example {@code full} or {@code compact}): its
 * content and a fingerprint of that content computed at load. Packs are immutable per run, so the
 * fingerprint is stable and can appear in governance evidence.
 *
 * @param name        the variant name; non-blank
 * @param content     the loaded content of the variant's file; never {@code null} (may be empty)
 * @param fingerprint the SHA-256 hex fingerprint of the content; non-blank
 */
public record ContextPackVariant(
    String name,
    String content,
    String fingerprint
) {

  public ContextPackVariant {
    Validate.notBlank(name, "ContextPackVariant name must not be blank");
    Validate.notNull(content, "ContextPackVariant content must not be null for variant: %s"
        .formatted(name));
    Validate.notBlank(fingerprint, "ContextPackVariant fingerprint must not be blank for variant: %s".formatted(name));
  }
}
