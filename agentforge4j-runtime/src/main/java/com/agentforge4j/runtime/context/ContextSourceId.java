// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.context;

import com.agentforge4j.core.workflow.step.ContextSelector;
import com.agentforge4j.util.Validate;

/**
 * Canonical identity for a {@link ContextSelector}'s underlying source, used to key compact siblings
 * and to match a downstream selector against the source a {@code COMPACT} step produced a sibling for.
 *
 * <p>Two selectors with the same {@code kind} and {@code ref} name the same source regardless of
 * {@code variant} — the canonical id deliberately excludes {@code variant} for that reason. The
 * {@code ref} is part of the identity verbatim, so a whole-ledger ref and a section subpath of the
 * same ledger (for example {@code requirements} vs {@code requirements.entries}) are two distinct
 * sources: a compact sibling produced for one never serves the other.
 */
public final class ContextSourceId {

  private ContextSourceId() {
  }

  /**
   * Returns the canonical source id for {@code selector}, in the form {@code "<kind>:<ref>"}.
   *
   * @param selector the selector to derive an id for; must not be {@code null}
   *
   * @return the canonical id; never {@code null}
   */
  public static String of(ContextSelector selector) {
    Validate.notNull(selector, "selector must not be null");
    return selector.kind().name() + ":" + selector.ref();
  }
}
