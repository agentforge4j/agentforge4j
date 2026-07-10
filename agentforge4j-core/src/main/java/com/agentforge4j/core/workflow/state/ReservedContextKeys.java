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

  /**
   * Reserved key prefix under which the persisted count of requested context expansions is stored,
   * one key per step execution uid. In the protected {@code __} namespace for the same reason as
   * {@link #LEDGER_KEY_PREFIX} — this total bounds {@code maxExpansions} across every
   * command-application batch belonging to the same step invocation (a pause/resume or retry that
   * reuses the same step execution uid), not just the current batch.
   */
  public static final String EXPANSION_COUNT_KEY_PREFIX = "__expansionCount.";

  /**
   * Reserved key prefix under which a {@code COMPACT} step's resolved source content is staged before
   * an {@code LLM_SUMMARY} invocation, one key per canonical source id. The runtime's agent invoker
   * only ever renders context from {@code state} via a {@code ContextMapping}, never from an arbitrary
   * caller-supplied string — this key is how the already-resolved source content is made addressable
   * that way, mirroring the {@code spar.resolution.prompt} convention {@code SparBehaviourHandler} uses
   * for the same reason. In the protected {@code __} namespace for the same reason as
   * {@link #LEDGER_KEY_PREFIX}.
   */
  public static final String LLM_SUMMARY_INPUT_KEY_PREFIX = "__compactSourceInput.";

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

  /**
   * Returns the reserved context key storing the persisted context-expansion count for the given
   * step execution uid.
   *
   * @param stepExecutionUid the step execution uid (see {@code ExecutionContext#allocateStepSequenceUid})
   *
   * @return the reserved key {@code "__expansionCount." + stepExecutionUid}; never {@code null}
   */
  public static String expansionCountKey(int stepExecutionUid) {
    return EXPANSION_COUNT_KEY_PREFIX + stepExecutionUid;
  }

  /**
   * Returns the reserved context key staging a COMPACT step's resolved source content for an
   * {@code LLM_SUMMARY} invocation, for the given canonical source id.
   *
   * @param sourceId the canonical source id (see {@code ContextSourceId} in the runtime); must not be
   *                 blank
   *
   * @return the reserved key {@code "__compactSourceInput." + sourceId}; never {@code null}
   */
  public static String llmSummaryInputKey(String sourceId) {
    Validate.notBlank(sourceId, "sourceId must not be blank");
    return LLM_SUMMARY_INPUT_KEY_PREFIX + sourceId;
  }
}
