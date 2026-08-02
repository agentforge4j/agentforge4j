// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

/**
 * A neutral, structural risk signal contributing to an execution estimate's uncertainty. Flags are
 * derived deterministically from workflow structure (and, for {@link #WIDE_TOKEN_ENVELOPE}, from the
 * aggregated range). They describe execution shape only — never money, billing, or provider cost.
 */
public enum RiskFlag {

  /** One or more loops end by agent signal or evaluator rather than a fixed count. */
  AGENT_DRIVEN_LOOP,

  /** The worst-case iteration expansion (product of loop caps) is large, though finite. */
  HIGH_ITERATION_CEILING,

  /** Routing depends on branch keys written from model output rather than fixed inputs. */
  LLM_DECIDED_BRANCHING,

  /** Structural nesting approaches the traversal limit. */
  DEEP_NESTING,

  /** The workflow contains a large number of steps. */
  LARGE_STRUCTURE,

  /** The estimated maximum token usage greatly exceeds the estimated minimum. */
  WIDE_TOKEN_ENVELOPE
}
