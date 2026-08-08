// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;

/**
 * Addresses one source of context by kind and reference. Identity is the pair: two
 * {@code ContextSource} values denote the same source when their {@link ContextSourceKind} and
 * their {@code ref} are both equal, which is what makes a reference unambiguous — the same text can
 * legitimately name a state key and an artifact.
 *
 * <p>Surrounding whitespace on the reference is removed, so {@code " shared-note "} and
 * {@code "shared-note"} address the same source. These references arrive in model-generated output,
 * where stray padding is a formatting artefact rather than a different name; treating the two as
 * distinct would turn it into a failed resolution and a wasted retry. Whitespace inside the
 * reference is part of the name and is left alone. A reference that is nothing but whitespace has
 * no name in it and is rejected rather than trimmed to empty.
 *
 * <p>This type addresses a source. It does not say how that source is rendered, whether it may be
 * read, or by whom — those are separate concerns owned elsewhere.
 *
 * @param kind the kind of source addressed; never {@code null}
 * @param ref  the reference resolving the source for {@code kind}; never blank, and stored with
 *             surrounding whitespace removed
 */
public record ContextSource(
    ContextSourceKind kind,
    String ref) {

  /**
   * Validates that a kind is present and the reference holds something other than whitespace, then
   * stores the reference without its surrounding whitespace. Blankness is judged before trimming,
   * so {@code null}, {@code ""} and a whitespace-only reference are all rejected and the stored
   * reference can never be empty.
   */
  public ContextSource {
    Validate.notNull(kind, "ContextSource kind must not be null");
    Validate.notBlank(ref, "ContextSource ref must not be blank");
    ref = ref.strip();
  }
}
