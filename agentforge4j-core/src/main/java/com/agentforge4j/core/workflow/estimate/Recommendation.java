// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.estimate;

/**
 * Neutral continue/narrow/stop recommendation an execution estimate offers to a human (or a calling
 * workflow). It informs a decision; it never enforces one — the estimator never blocks use of the
 * framework.
 *
 * <ul>
 *   <li>{@link #CONTINUE} — the estimated shape is bounded and predictable enough to proceed.</li>
 *   <li>{@link #NARROW} — consider reducing scope; the envelope is wide or the risk is high.</li>
 *   <li>{@link #STOP} — advise stopping to reconsider (e.g. no finite ceiling could be derived).
 *       Advisory only.</li>
 * </ul>
 */
public enum Recommendation {
  CONTINUE,
  NARROW,
  STOP
}
