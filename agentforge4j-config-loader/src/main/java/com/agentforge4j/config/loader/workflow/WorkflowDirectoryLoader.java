// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import java.nio.file.Path;

/**
 * Loads workflow bundles from an explicit filesystem root.
 */
@FunctionalInterface
public interface WorkflowDirectoryLoader {

  WorkflowDirectoryLoad loadWorkflows(Path root);
}
