// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step;

import com.agentforge4j.util.Validate;

/**
 * A single declared context source a step receives, or may request within its expandable scope.
 *
 * @param kind    the kind of source; never {@code null}
 * @param ref     the reference resolving the source for {@code kind} (ledger id + optional section,
 *                artifact id, pack name, state key, or step id); non-blank
 * @param variant which form of the source to use; {@code null} defaults to {@link ContextVariant#FULL}
 */
public record ContextSelector(
    ContextSourceKind kind,
    String ref,
    ContextVariant variant
) {

  public ContextSelector {
    Validate.notNull(kind, "ContextSelector kind must not be null");
    Validate.notBlank(ref, "ContextSelector ref must not be blank");
    variant = variant != null ? variant : ContextVariant.FULL;
  }
}
