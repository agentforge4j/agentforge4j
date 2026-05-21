package com.agentforge4j.core.workflow.state;

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

  private ReservedContextKeys() {
  }
}
