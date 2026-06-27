// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.reachability;

import com.agentforge4j.core.workflow.WorkflowDefinition;

/**
 * Resolves a {@code WORKFLOW} behaviour's {@code workflowRef} to its definition while walking the reachable workflow
 * graph.
 *
 * <p>Implementations must return {@code null} (never throw) when no definition exists for the given
 * ref, so the reachable-graph walk can simply skip an unresolvable sub-workflow rather than fail — matching the
 * runtime's {@code repository.findAll().get(ref)} lookup semantics.
 */
@FunctionalInterface
public interface WorkflowRefResolver {

  /**
   * Resolves a sub-workflow reference.
   *
   * @param workflowRef the {@code workflowRef} to resolve
   *
   * @return the referenced definition, or {@code null} when no definition exists for {@code workflowRef}
   */
  WorkflowDefinition resolve(String workflowRef);
}
