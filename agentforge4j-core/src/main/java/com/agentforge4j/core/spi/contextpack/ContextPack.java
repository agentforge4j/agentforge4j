// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.contextpack;

import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.Map;

/**
 * A named, versioned, fingerprinted bundle of context content, referenced by a
 * {@code ContextSelector} of kind {@code CONTEXT_PACK}. Packs exclude irrelevant context and sit in
 * the stable prompt prefix so they cache; the per-variant fingerprints make selection auditable.
 *
 * @param name        the pack name (referenced by selectors); non-blank
 * @param version     the pack version; non-blank
 * @param description optional description; may be {@code null}
 * @param tags        classification tags; never {@code null} ({@code null} becomes an empty list)
 * @param variants    the loaded variants keyed by variant name; never empty; each key must equal
 *                    the corresponding variant's {@link ContextPackVariant#name()}
 */
public record ContextPack(
    String name,
    String version,
    String description,
    List<String> tags,
    Map<String, ContextPackVariant> variants
) {

  public ContextPack {
    Validate.notBlank(name, "ContextPack name must not be blank");
    Validate.notBlank(version, "ContextPack version must not be blank for pack: %s".formatted(name));
    tags = tags != null ? List.copyOf(tags) : List.of();
    Validate.notNull(variants, "ContextPack variants must not be null for pack: %s".formatted(name));
    Validate.isTrue(!variants.isEmpty(),
        "ContextPack must declare at least one variant for pack: %s".formatted(name));
    for (Map.Entry<String, ContextPackVariant> entry : variants.entrySet()) {
      Validate.isTrue(entry.getKey().equals(entry.getValue().name()),
          "ContextPack variant key '%s' must match variant name '%s' for pack: %s"
              .formatted(entry.getKey(), entry.getValue().name(), name));
    }
    variants = Map.copyOf(variants);
  }
}
