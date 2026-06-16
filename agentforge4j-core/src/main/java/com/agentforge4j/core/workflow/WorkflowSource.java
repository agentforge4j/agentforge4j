// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow;

/**
 * Indicates the origin of a workflow definition.
 */
public enum WorkflowSource {
  /**
   * Workflow loaded from a file on disk.
   */
  SHIPPED,
  /**
   * Workflow created by a user.
   */
  CUSTOM,
  /**
   * Workflow created by forking an existing workflow.
   */
  FORKED,
  /**
   * Workflow created by editing an existing workflow in draft mode.
   */
  GENERATED_DRAFT
}
