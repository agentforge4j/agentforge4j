// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.step.behaviour;

/**
 * Scope of execution retried when {@link RetryPreviousBehaviour} is used.
 */
public enum RetryMode {
  /**
   * Re-executes only the step that failed or requested retry.
   */
  SINGLE_STEP,
  /**
   * Re-executes from an earlier step onward; which steps rerun is defined by the runtime.
   */
  FROM_STEP
}
