package com.agentforge4j.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Spring Boot configuration under {@code agentforge4j.*} that controls classpath and
 * filesystem loading of workflow and agent definitions and optional runtime nesting limits.
 *
 * @param agentsPath filesystem directory of agent YAML (or blank to skip filesystem agent loading)
 * @param workflowsPath filesystem directory of workflow YAML (or blank to skip filesystem workflow
 *     loading)
 * @param maxNestingDepth when non-null, forwarded through {@link com.agentforge4j.runtime.WorkflowRuntimeBuilder};
 *     when {@code null} construction keeps framework defaults
 * @param loadShippedAgents when {@code true}, merges agents from the starter's shipped classpath
 *     resources
 * @param loadShippedWorkflows when {@code true}, merges workflows from shipped classpath resources
 */
@ConfigurationProperties(prefix = "agentforge4j")
public record AgentForge4jProperties(
    String agentsPath,
    String workflowsPath,
    Integer maxNestingDepth,
    boolean loadShippedWorkflows,
    boolean loadShippedAgents
) {
}
