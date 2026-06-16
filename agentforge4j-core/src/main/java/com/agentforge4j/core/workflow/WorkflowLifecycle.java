// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

/**
 * Tracks the maturity state of a workflow definition.
 */
public enum WorkflowLifecycle {
  /**
   * Initial state when a workflow is first defined or loaded.
   */
  DRAFT,
  /**
   * Indicates the workflow has been validated and is ready for installation.
   */
  VALIDATED,
  /**
   * The workflow has been installed and is available for execution.
   */
  INSTALLED,
  /**
   * The workflow is currently executing or has been executed at least once.
   */
  ACTIVE
}
