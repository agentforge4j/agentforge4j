package com.agentforge4j.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for AgentForge4J OSS runtime loading.
 *
 * <p>All properties live under the {@code agentforge4j} prefix.
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
