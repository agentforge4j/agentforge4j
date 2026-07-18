// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.state;

/**
 * Reserved context key constants written by the runtime. Keys in the {@code __} namespace
 * (double-underscore prefix) are protected from {@code clearEntriesFromUid} resets and must not be
 * used by workflow authors or agents.
 */
public final class ReservedContextKeys {

  /**
   * Prefix of the reserved runtime-owned context namespace. Keys starting with this prefix back
   * runtime governance state (retry-attempt counters, token totals, dispatch markers) and must
   * never be writable from workflow definitions, LLM commands, or end-user input.
   */
  public static final String RESERVED_PREFIX = "__";

  /**
   * Running total of tokens consumed across all LLM calls in the current run. Written by the
   * runtime after each LLM invocation. Null token counts (providers that do not report usage)
   * contribute zero to this total.
   */
  public static final String LLM_TOKENS_TOTAL = "__llm_tokens_total";

  /**
   * Returns whether {@code key} lies in the reserved runtime-owned {@code __} namespace.
   *
   * @param key context key to test; may be {@code null} (returns {@code false})
   * @return {@code true} when the key starts with {@link #RESERVED_PREFIX}
   */
  public static boolean isReserved(String key) {
    return key != null && key.startsWith(RESERVED_PREFIX);
  }

  private ReservedContextKeys() {
  }
}
