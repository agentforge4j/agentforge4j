// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader;

/**
 * Loads workflow definitions from a backing source.
 */
public interface WorkflowLoader {

  /**
   * Loads workflows from the backing source.
   *
   * @return loaded workflows with any bundled agents
   * @throws RuntimeException when the source cannot be read or contains invalid definitions
   */
  WorkflowDirectoryLoad loadWorkflows();

}
