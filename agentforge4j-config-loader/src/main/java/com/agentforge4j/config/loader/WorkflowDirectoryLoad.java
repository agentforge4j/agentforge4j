package com.agentforge4j.config.loader;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;

import java.util.Map;

/**
 * Result of loading a workflows directory or shipped workflow bundles: workflow definitions plus
 * agents discovered under each workflow's {@code agents/} tree (merged into a single map by id).
 *
 * @param workflows loaded workflows keyed by id
 * @param bundledAgents agents discovered in workflow bundles keyed by id
 */
public record WorkflowDirectoryLoad(
    Map<String, WorkflowDefinition> workflows,
    Map<String, AgentDefinition> bundledAgents
) {

  public WorkflowDirectoryLoad {
    Validate.notNull(workflows, "workflows must not be null");
    Validate.notNull(bundledAgents, "bundledAgents must not be null");
    workflows = Map.copyOf(workflows);
    bundledAgents = Map.copyOf(bundledAgents);
  }
}
