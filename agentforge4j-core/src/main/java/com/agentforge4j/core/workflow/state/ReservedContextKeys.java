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
}
