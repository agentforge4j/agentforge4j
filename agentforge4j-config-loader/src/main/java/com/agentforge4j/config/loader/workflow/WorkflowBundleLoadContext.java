// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.util.Validate;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Immutable context for a single workflow bundle loading invocation.
 */
public record WorkflowBundleLoadContext(Optional<Path> workflowRoot) {

  public WorkflowBundleLoadContext {
    Validate.notNull(workflowRoot, "workflowRoot must not be null");
  }

  public static WorkflowBundleLoadContext classpath() {
    return new WorkflowBundleLoadContext(Optional.empty());
  }

  public static WorkflowBundleLoadContext filesystem(Path root) {
    return new WorkflowBundleLoadContext(
        Optional.of(Validate.notNull(root, "root must not be null")));
  }

  public Path requireWorkflowRoot() {
    return workflowRoot.orElseThrow(() -> new IllegalStateException(
        "Workflow root is required for filesystem workflow loading"));
  }
}
