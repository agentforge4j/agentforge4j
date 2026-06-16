// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;
import java.util.Map;

/**
 * Immutable snapshot of loaded agents and workflows.
 *
 * @param agents    loaded agents keyed by id
 * @param workflows loaded workflows keyed by id
 */
public record LoadedConfiguration(
    Map<String, AgentDefinition> agents,
    Map<String, WorkflowDefinition> workflows
) {

  public LoadedConfiguration {
    Validate.notNull(agents, "LoadedConfiguration agents must not be null");
    Validate.notNull(workflows, "LoadedConfiguration workflows must not be null");
    agents = Map.copyOf(agents);
    workflows = Map.copyOf(workflows);
  }
}
