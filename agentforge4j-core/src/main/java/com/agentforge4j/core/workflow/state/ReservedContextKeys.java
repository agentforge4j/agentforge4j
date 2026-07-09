// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

import com.agentforge4j.util.Validate;

/**
 * Reserved context key constants written by the runtime. Keys in the {@code __} namespace
 * (double-underscore prefix) are protected from {@code clearEntriesFromUid} resets and must not be
 * used by workflow authors or agents.
 */
public final class ReservedContextKeys {

  /**
   * Running total of tokens consumed across all LLM calls in the current run. Written by the
   * runtime after each LLM invocation. Null token counts (providers that do not report usage)
   * contribute zero to this total.
   */
  public static final String LLM_TOKENS_TOTAL = "__llm_tokens_total";

  /**
   * Reserved key prefix under which a declared ledger's merged section is stored, one key per ledger
   * id. In the protected {@code __} namespace so a ledger section survives {@code clearEntriesFromUid}
   * resets and cannot be overwritten by workflow authors or agents.
   */
  public static final String LEDGER_KEY_PREFIX = "__ledger.";

  /**
   * Reserved key prefix under which a compaction step's compact sibling is stored, one key per
   * canonical source id. In the protected {@code __} namespace for the same reason as
   * {@link #LEDGER_KEY_PREFIX}.
   */
  public static final String COMPACT_KEY_PREFIX = "__compact.";

  /**
   * Reserved key prefix under which content served by a granted context-expansion request is
   * stored, one key per canonical source id. In the protected {@code __} namespace for the same
   * reason as {@link #LEDGER_KEY_PREFIX} — a granted expansion is runtime bookkeeping and must
   * never collide with (or be overwritten by) author- or agent-owned context keys.
   */
  public static final String GRANTED_KEY_PREFIX = "__granted.";

  private ReservedContextKeys() {
  }

  /**
   * Returns the reserved context key storing the merged section of the ledger with the given id.
   *
   * @param ledgerId the ledger id; must not be blank
   *
   * @return the reserved key {@code "__ledger." + ledgerId}; never {@code null}
   */
  public static String ledgerKey(String ledgerId) {
    Validate.notBlank(ledgerId, "ledgerId must not be blank");
    return LEDGER_KEY_PREFIX + ledgerId;
  }

  /**
   * Returns the reserved context key storing the compact sibling for the given canonical source id.
   *
   * @param sourceId the canonical source id (see {@code ContextSourceId} in the runtime); must not be
   *                 blank
   *
   * @return the reserved key {@code "__compact." + sourceId}; never {@code null}
   */
  public static String compactKey(String sourceId) {
    Validate.notBlank(sourceId, "sourceId must not be blank");
    return COMPACT_KEY_PREFIX + sourceId;
  }

  /**
   * Returns the reserved context key storing granted context-expansion content for the given
   * canonical source id.
   *
   * @param sourceId the canonical source id (see {@code ContextSourceId} in the runtime); must not
   *                 be blank
   *
   * @return the reserved key {@code "__granted." + sourceId}; never {@code null}
   */
  public static String grantedKey(String sourceId) {
    Validate.notBlank(sourceId, "sourceId must not be blank");
    return GRANTED_KEY_PREFIX + sourceId;
  }
}
